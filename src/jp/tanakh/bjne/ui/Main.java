package jp.tanakh.bjne.ui;

import java.awt.Button;
import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

import javax.sound.sampled.LineUnavailableException;

import jp.tanakh.bjne.nes.Nes;
import jp.tanakh.bjne.nes.Renderer;

public class Main extends Frame {

	private static final long serialVersionUID = 1L;

	private Nes nes = null;
	private AWTRenderer r = null;
	private FPSTimer timer = new FPSTimer();

	private Object nesLock = new Object();

	public static void main(String[] args) {
		new Main(args.length == 1 ? args[0] : null);
	}

	Main(String file) {
		// setup main window

		super("Nes Emulator");

		MenuBar menuBar = new MenuBar();
		setMenuBar(menuBar);

		{
			Menu menu = new Menu("File");
			{
				MenuItem item = new MenuItem("Open");
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						onOpen();
					}
				});
				menu.add(item);
			}
			{
				MenuItem item = new MenuItem("Exit");
				item.addActionListener(new ActionListener() {
					@Override
					public void actionPerformed(ActionEvent e) {
						onExit();
					}
				});
				menu.add(item);
			}
			menuBar.add(menu);
		}
		{
			Menu menu = new Menu("Help");
			MenuItem item = new MenuItem("About");
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					onAbout();
				}
			});
			menu.add(item);
			menuBar.add(menu);
		}

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				onExit();
			}
		});

		initializeNes();

		if (file != null)
			openRom(file);

		setVisible(true);
		setVisible(false);
		setSize(256 + getInsets().left + getInsets().right, 240
				+ getInsets().top + getInsets().bottom);
		setVisible(true);

		loop();
	}

	private void loop() {
		final int FPS = 60;

		for (;;) {
			synchronized (nesLock) {
				if (nes == null)
					continue;

				long start = System.nanoTime();
				nes.execFrame();

				for (;;) {
					int bufStat = r.getSoundBufferState();
					if (bufStat < 0)
						break;
					if (bufStat == 0) {
						long elapsed = System.nanoTime() - start;
						long wait = (long) (1.0 / FPS - elapsed / 1e-9);
						try {
							if (wait > 0)
								Thread.sleep(wait);
						} catch (InterruptedException e) {
						}
						break;
					}
					try {
						Thread.sleep(1);
					} catch (InterruptedException e) {
					}
				}
				// timer.elapse(60);
			}
		}
	}

	private void initializeNes() {
		try {
			r = new AWTRenderer(this);
		} catch (LineUnavailableException e) {
			System.out.println("Cannot initialize Renderer.");
			e.printStackTrace();
			System.exit(0);
		}
	}

	private void openRom(String file) {
		System.out.println("loading " + file);
		synchronized (nesLock) {
			try {
				nes = new Nes(r);
				nes.load(file);
				nes.reset();
			} catch (IOException e) {
				System.out.println("error: loading " + file + " ("
						+ e.getMessage() + ")");
				nes = null;
			}
		}
	}

	private void onOpen() {
		FileDialog d = new FileDialog(this, "Open ROM", FileDialog.LOAD);
		d.setVisible(true);
		String dir = d.getDirectory();
		String file = d.getFile();
		openRom(dir + file);
	}

	private void onExit() {
		System.exit(0);
	}

	private class AboutDialog extends Dialog {
		private static final long serialVersionUID = 1L;

		AboutDialog(Frame owner) {
			super(owner);
			setLayout(new FlowLayout());

			add(new Label("Beautiful Japanese Nes Emulator for Java"));
			add(new Label("Version 0.1.0"));

			Button b = new Button("OK");
			b.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					setVisible(false);
				}
			});
			add(b);

			addWindowListener(new WindowAdapter() {
				@Override
				public void windowClosing(WindowEvent e) {
					setVisible(false);
				}
			});

			setTitle("About");
			setSize(270, 100);
		}
	}

	private void onAbout() {
		Dialog dlg = new AboutDialog(this);
		dlg.setModal(true);
		dlg.setVisible(true);
	}
}
