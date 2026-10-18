package com.pssst.runelite;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
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
 * Side panel that gets the phone paired: what to type into the Pssst app (this PC's address and the Sync Token) and
 * whether the Local Server can currently be reached. Read-only: it changes no settings and makes no network calls.
 * The token is masked until the player shows it, and masks itself again after a minute.
 */
class PairingPanel extends PluginPanel
{
	private static final int TOKEN_VISIBLE_MS = 60_000;

	private final CompanionConfig config;
	private final ScheduledExecutorService executor;
	private final BooleanSupplier serverRunning;

	private final JLabel status = new JLabel();
	private final JPanel addresses = new JPanel();
	private final JTextField token = new JTextField();
	private final JButton showHide = new JButton("Show");
	private final Timer remask = new Timer(TOKEN_VISIBLE_MS, e -> setTokenVisible(false));
	private boolean tokenVisible;

	PairingPanel(CompanionConfig config, ScheduledExecutorService executor, BooleanSupplier serverRunning)
	{
		this.config = config;
		this.executor = executor;
		this.serverRunning = serverRunning;
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

		body.add(heading("Server URL for the app"));
		addresses.setLayout(new BoxLayout(addresses, BoxLayout.Y_AXIS));
		addresses.setOpaque(false);
		addresses.setAlignmentX(LEFT_ALIGNMENT);
		body.add(addresses);
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

		JLabel help = new JLabel("<html>Type these into the Pssst app on your phone. Your phone must be on the same "
			+ "Wi-Fi, or on Tailscale. Nothing here is sent anywhere.</html>");
		help.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		help.setAlignmentX(LEFT_ALIGNMENT);
		body.add(help);
		body.add(gap());

		JButton refresh = new JButton("Refresh");
		refresh.setAlignmentX(LEFT_ALIGNMENT);
		refresh.addActionListener(e -> refresh());
		body.add(refresh);

		add(body, BorderLayout.NORTH);
		renderToken();
		refresh();
	}

	/** Re-reads the settings and the adapters. The adapter scan runs on the shared executor, never on the Swing thread. */
	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			status.setText(statusText(config.enableSync(), config.enableLocalServer(), config.bindLoopbackOnly(), serverRunning.getAsBoolean()));
			renderToken();
		});
		executor.execute(() ->
		{
			List<LanAddresses.Address> found = LanAddresses.scan();
			SwingUtilities.invokeLater(() -> renderAddresses(found));
		});
	}

	void dispose()
	{
		remask.stop();
	}

	/** What the player needs to know about the server right now, in plain words. */
	static String statusText(boolean enableSync, boolean enableLocalServer, boolean loopbackOnly, boolean running)
	{
		if (!enableSync)
		{
			return "<html><b>Sync is off.</b> Turn on <i>Enable Sync</i> in the Pssst Companion settings.</html>";
		}
		if (!enableLocalServer)
		{
			return "<html><b>Local Server is off.</b> Turn on <i>Enable Local Server</i> in the settings.</html>";
		}
		if (!running)
		{
			return "<html><b>The server is not running.</b> The port may be in use; try a different <i>Port</i>.</html>";
		}
		if (loopbackOnly)
		{
			return "<html><b>This PC only.</b> Your phone cannot connect yet. Turn off <i>This PC Only</i> in the "
				+ "Local Server settings.</html>";
		}
		return "<html><b>Ready.</b> Your phone can connect over your home network.</html>";
	}

	private void renderAddresses(List<LanAddresses.Address> found)
	{
		addresses.removeAll();
		if (found.isEmpty())
		{
			addresses.add(plain("No home-network address found. Check that this PC is connected to Wi-Fi or Ethernet."));
		}
		for (LanAddresses.Address address : found)
		{
			String url = LanAddresses.url(address.host, config.localServerPort());
			addresses.add(plain(address.label));
			JTextField field = new JTextField(url);
			field.setEditable(false);
			field.setAlignmentX(LEFT_ALIGNMENT);
			field.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
			addresses.add(field);
			JButton copy = new JButton("Copy");
			copy.addActionListener(e -> copy(url));
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
			row.setOpaque(false);
			row.setAlignmentX(LEFT_ALIGNMENT);
			row.add(copy);
			addresses.add(row);
		}
		addresses.revalidate();
		addresses.repaint();
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

	private static JLabel plain(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
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
