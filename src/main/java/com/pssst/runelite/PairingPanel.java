package com.pssst.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel for pairing and checking on the phone: whether the last snapshot reached the Pssst app, the Sync Token
 * (masked until shown; masks itself again after a minute) and the Phone Address, both with an explicit Save button
 * (RuneLite's settings fields only save once they lose focus, which is easy to miss). Makes no network calls.
 */
class PairingPanel extends PluginPanel
{
	private static final int TOKEN_VISIBLE_MS = 60_000;
	private static final int STATUS_REFRESH_MS = 30_000;
	private static final String MASK = "••••••••••••";

	/** Shortest Sync Token that isn't easy to guess (the plugin generates 32 characters). */
	static final int MIN_TOKEN_LENGTH = 16;

	private final CompanionConfig config;
	private final Supplier<String> statusHtml;
	private final BiConsumer<String, String> setConfig;
	private final Supplier<String> tokenGenerator;
	private final Runnable sendNow;

	private final JLabel status = new JLabel();
	private final JTextField token = new JTextField();
	private final JLabel tokenNote = new JLabel();
	private final JButton showHide = new JButton("Show");
	private final JTextField address = new JTextField();
	private final Timer remask = new Timer(TOKEN_VISIBLE_MS, e -> setTokenVisible(false));
	private final Timer ticker = new Timer(STATUS_REFRESH_MS, e -> refresh());
	private boolean tokenVisible;

	PairingPanel(CompanionConfig config, Supplier<String> statusHtml, BiConsumer<String, String> setConfig,
		Supplier<String> tokenGenerator)
	{
		this(config, statusHtml, setConfig, tokenGenerator, () -> {});
	}

	PairingPanel(CompanionConfig config, Supplier<String> statusHtml, BiConsumer<String, String> setConfig,
		Supplier<String> tokenGenerator, Runnable sendNow)
	{
		this.config = config;
		this.statusHtml = statusHtml;
		this.setConfig = setConfig;
		this.tokenGenerator = tokenGenerator;
		this.sendNow = sendNow != null ? sendNow : () -> {};
		remask.setRepeats(false);

		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);

		body.add(heading("Pssst Companion"));
		status.setAlignmentX(LEFT_ALIGNMENT);
		body.add(status);
		body.add(gap());

		// Sync Token: shown and editable only after Show
		body.add(heading("Sync Token"));
		fill(token);
		token.setEditable(false);
		body.add(token);
		JButton saveToken = new JButton("Save");
		saveToken.addActionListener(e -> saveToken());
		token.addActionListener(e -> saveToken());
		showHide.addActionListener(e -> setTokenVisible(!tokenVisible));
		JButton copyToken = new JButton("Copy");
		copyToken.addActionListener(e -> copy(config.syncToken()));
		JButton newToken = new JButton("New");
		newToken.addActionListener(e -> makeNewToken());
		body.add(buttons(showHide, saveToken, copyToken, newToken));
		tokenNote.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tokenNote.setAlignmentX(LEFT_ALIGNMENT);
		body.add(tokenNote);
		body.add(gap());

		// Phone Address
		body.add(heading("Phone Address"));
		fill(address);
		address.addActionListener(e -> saveAddress());
		body.add(address);
		JButton saveAddress = new JButton("Save");
		saveAddress.addActionListener(e -> saveAddress());
		JButton sendNowButton = new JButton("Send Now");
		sendNowButton.addActionListener(e -> this.sendNow.run());
		body.add(buttons(saveAddress, sendNowButton));
		body.add(gap());

		JLabel help = new JLabel("<html>To pair: type the Sync Token (or your own word like <i>Goblin</i>) into the Pssst app, "
			+ "and type the address the app shows into Phone Address here.</html>");
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		help.setAlignmentX(LEFT_ALIGNMENT);
		body.add(help);

		add(body, BorderLayout.NORTH);
		ticker.start();
		refresh();
	}

	/** Re-reads the status, token and address; safe to call from any thread. Never overwrites a field being edited. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(statusHtml.get());
			if (!token.isFocusOwner())
			{
				renderToken();
			}
			if (!address.isFocusOwner())
			{
				address.setText(config.phoneAddress());
			}
		});
	}

	void dispose()
	{
		remask.stop();
		ticker.stop();
	}

	private void saveToken()
	{
		if (!tokenVisible)
		{
			return;
		}
		String value = token.getText().trim();
		if (value.isEmpty())
		{
			JOptionPane.showMessageDialog(this, "The Sync Token can't be empty. Use New to make one.");
			return;
		}
		setConfig.accept("syncToken", value);
		setTokenVisible(false);
		sendNow.run();
	}

	private void makeNewToken()
	{
		int answer = JOptionPane.showConfirmDialog(this,
			"Make a new Sync Token? Your phone stops receiving until you type the new one into the Pssst app.",
			"New Sync Token", JOptionPane.OK_CANCEL_OPTION);
		if (answer == JOptionPane.OK_OPTION)
		{
			setConfig.accept("syncToken", tokenGenerator.get());
			setTokenVisible(true);
			sendNow.run();
		}
	}

	private void saveAddress()
	{
		setConfig.accept("phoneAddress", address.getText().trim());
		refresh();
		sendNow.run();
	}

	/** What the player needs to know about sending to the phone right now, in plain words. */
	/** After this long without reaching the phone, the panel suggests its address may have changed. */
	static final long ADDRESS_HINT_AFTER_MS = 30 * 60_000L;

	static String describe(boolean enableSync, boolean sendToPhone, String addressProblem, DeliveryPolicy.State state,
		boolean hasData, long lastDeliveredAt, long waitingSince, long now)
	{
		if (!enableSync)
		{
			return html("Sync is off.", "Turn on <i>Enable Sync</i> in the Pssst Companion settings.");
		}
		if (!sendToPhone)
		{
			return html("Not sending to your phone.", "Turn on <i>Send to My Phone</i> in the settings.");
		}
		if (addressProblem != null)
		{
			return html("Check the Phone Address.", addressProblem);
		}
		String last = lastDeliveredAt > 0 ? " Last delivered " + ago(now - lastDeliveredAt) + "." : "";
		switch (state)
		{
			case DELIVERED:
				return html("Your phone is up to date.", "It gets changes at most every 2 minutes while you play." + last);
			case WAITING:
				if (waitingSince > 0 && now - waitingSince >= ADDRESS_HINT_AFTER_MS)
				{
					return html("Can't reach your phone (" + ago(now - waitingSince).replace(" ago", "") + ").",
						"If the Pssst app is open on the same Wi-Fi, your phone's address may have changed: the app "
							+ "shows the current one. Update Phone Address below." + last);
				}
				return html("Waiting for your phone.", "The Pssst app isn't listening right now (closed, or not on the "
					+ "same Wi-Fi). It will get the latest as soon as it is." + last);
			case REJECTED:
				return html("Your phone refused the Sync Token.", "Type the Sync Token below into the Pssst app again.");
			case PAUSED:
				return html("Sending is paused for a moment.", "The app asked to wait. Nothing to do." + last);
			default:
				return hasData
					? html("Sending to your phone…", "Open the Pssst app on your phone if it doesn't arrive.")
					: html("Nothing to send yet.", "Log in once and Pssst will send your data.");
		}
	}

	/** Any non-empty Sync Token is valid over private LAN / Tailscale; null when fine. */
	static String tokenNote(String token)
	{
		return null;
	}

	static String ago(long ms)
	{
		long minutes = Math.max(0, ms / 60_000L);
		if (minutes < 1)
		{
			return "just now";
		}
		if (minutes < 60)
		{
			return minutes + " min ago";
		}
		long hours = minutes / 60;
		return hours < 48 ? hours + " h ago" : (hours / 24) + " days ago";
	}

	private static String html(String headline, String detail)
	{
		return "<html><b>" + headline + "</b> " + detail + "</html>";
	}

	private void setTokenVisible(boolean visible)
	{
		tokenVisible = visible;
		if (visible)
		{
			remask.restart();
		}
		else
		{
			remask.stop();
		}
		renderToken();
	}

	private void renderToken()
	{
		String value = config.syncToken();
		boolean hasToken = value != null && !value.isEmpty();
		token.setEditable(tokenVisible);
		token.setText(!hasToken ? "" : tokenVisible ? value : MASK);
		showHide.setText(tokenVisible ? "Hide" : "Show");
		String note = tokenNote(value);
		tokenNote.setText(note == null ? "" : note);
	}

	private static void copy(String text)
	{
		if (text != null && !text.isEmpty())
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
	}

	private static void fill(JTextField field)
	{
		field.setAlignmentX(LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
	}

	private static JPanel buttons(JButton... buttons)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		for (JButton button : buttons)
		{
			row.add(button);
		}
		return row;
	}

	private static JLabel heading(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(Color.WHITE);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static JPanel gap()
	{
		JPanel gap = new JPanel();
		gap.setOpaque(false);
		gap.setPreferredSize(new Dimension(1, 10));
		gap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 10));
		return gap;
	}
}
