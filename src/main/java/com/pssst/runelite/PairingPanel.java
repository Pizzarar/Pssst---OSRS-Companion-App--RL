package com.pssst.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel for pairing and checking on the phone: whether the last snapshot reached the Pssst app, and the Sync Token
 * to type into the app (masked until shown; it masks itself again after a minute). Read-only: it changes no settings and
 * makes no network calls.
 */
class PairingPanel extends PluginPanel
{
	private static final int TOKEN_VISIBLE_MS = 60_000;
	private static final int STATUS_REFRESH_MS = 30_000;

	private final CompanionConfig config;
	private final Supplier<String> statusHtml;

	private final JLabel status = new JLabel();
	private final JTextField token = new JTextField();
	private final JButton showHide = new JButton("Show");
	private final Timer remask = new Timer(TOKEN_VISIBLE_MS, e -> setTokenVisible(false));
	private final Timer ticker = new Timer(STATUS_REFRESH_MS, e -> refresh());
	private boolean tokenVisible;

	PairingPanel(CompanionConfig config, Supplier<String> statusHtml)
	{
		this.config = config;
		this.statusHtml = statusHtml;
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

		body.add(heading("Sync Token"));
		token.setEditable(false);
		token.setAlignmentX(LEFT_ALIGNMENT);
		token.setMaximumSize(new Dimension(Integer.MAX_VALUE, token.getPreferredSize().height));
		body.add(token);
		JPanel tokenButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
		tokenButtons.setOpaque(false);
		tokenButtons.setAlignmentX(LEFT_ALIGNMENT);
		showHide.addActionListener(e -> setTokenVisible(!tokenVisible));
		JButton copyToken = new JButton("Copy");
		copyToken.addActionListener(e -> copy(config.syncToken()));
		tokenButtons.add(showHide);
		tokenButtons.add(copyToken);
		body.add(tokenButtons);
		body.add(gap());

		JLabel help = new JLabel("<html>To pair: type the Sync Token into the Pssst app, then type the address the app "
			+ "shows into <i>Phone Address</i> in this plugin's settings. Your phone must be on the same Wi-Fi, or on "
			+ "Tailscale.</html>");
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		help.setAlignmentX(LEFT_ALIGNMENT);
		body.add(help);

		add(body, BorderLayout.NORTH);
		ticker.start();
		refresh();
	}

	/** Re-reads the status and the token; safe to call from any thread. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(statusHtml.get());
			renderToken();
		});
	}

	void dispose()
	{
		remask.stop();
		ticker.stop();
	}

	/** What the player needs to know about sending to the phone right now, in plain words. */
	static String describe(boolean enableSync, boolean sendToPhone, String addressProblem, DeliveryPolicy.State state,
		boolean hasData, long lastDeliveredAt, long now)
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
				return html("Waiting for your phone.", "The Pssst app isn't listening right now (closed, or not on the "
					+ "same Wi-Fi). It will get the latest as soon as it is." + last);
			case REJECTED:
				return html("Your phone refused the Sync Token.", "Copy the Sync Token below into the Pssst app again.");
			case PAUSED:
				return html("Sending is paused for a moment.", "The app asked to wait. Nothing to do." + last);
			default:
				return hasData
					? html("Sending to your phone…", "Open the Pssst app on your phone if it doesn't arrive.")
					: html("Nothing to send yet.", "Log in once and Pssst will send your data.");
		}
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
		token.setText(!hasToken ? "(not created yet)" : tokenVisible ? value : "••••••••••••");
		showHide.setText(tokenVisible ? "Hide" : "Show");
		showHide.setEnabled(hasToken);
	}

	private static void copy(String text)
	{
		if (text != null && !text.isEmpty())
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
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
