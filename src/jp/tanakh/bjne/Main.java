package jp.tanakh.bjne;

import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

public class Main extends Frame {

	private static final long serialVersionUID = 1L;

	private Nes nes = null;
	private Renderer r = null;
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
		for (;;) {
			synchronized (nesLock) {
				if (nes != null) {
					nes.execFrame();
					timer.elapse(60);
				}
			}
		}
	}

	private void initializeNes() {
		r = new AWTRenderer(this);
	}

	private void openRom(String file) {
		System.out.println("loading " + file);
		synchronized (nesLock) {
			try {
				nes = new Nes(r);
				nes.load(file);
				nes.reset();
				// nes.getCpu().setLogging(true);
			} catch (IOException e) {
				System.out.println("error: loading " + file);
				e.printStackTrace();
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

	private void onAbout() {
	}
}
