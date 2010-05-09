package jp.tanakh.bjne.ui;

import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

import jp.tanakh.bjne.nes.Renderer;

public class AWTRenderer implements Renderer {
	private static final int SCREEN_WIDTH = 256;
	private static final int SCREEN_HEIGHT = 240;

	private static final int SAMPLE_RATE = 48000;
	private static final int BPS = 16;
	private static final int CHANNELS = 2;
	private static final int BUFFER_FRAMES = 2;

	private static final int FPS = 60;
	private static final int SAMPLES_PER_FRAME = SAMPLE_RATE / FPS;

	private ScreenInfo scri = new ScreenInfo();
	private SoundInfo sndi = new SoundInfo();
	private InputInfo inpi = new InputInfo();

	private Frame frame;
	private BufferedImage image = new BufferedImage(SCREEN_WIDTH,
			SCREEN_HEIGHT, BufferedImage.TYPE_3BYTE_BGR);

	private SourceDataLine line;
	private int lineBufferSize;

	AWTRenderer(Frame f) throws LineUnavailableException {
		frame = f;
		frame.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(KeyEvent e) {
				onKey(e.getKeyCode(), true);
			}

			@Override
			public void keyReleased(KeyEvent e) {
				onKey(e.getKeyCode(), false);
			}
		});

		AudioFormat format = new AudioFormat(SAMPLE_RATE, BPS, CHANNELS, true,
				false);
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
		line = (SourceDataLine) AudioSystem.getLine(info);
		line.open();
		line.start();
		lineBufferSize = line.available();

		int bufSamples = SAMPLES_PER_FRAME;

		sndi.bps = 16;
		sndi.buf = new byte[bufSamples * (BPS / 8) * CHANNELS];
		sndi.ch = 2;
		sndi.freq = SAMPLE_RATE;
		sndi.sample = bufSamples;

		inpi.buf = new int[16];
	}

	@Override
	public void outputMessage(String msg) {
		System.out.println(msg);
	}

	@Override
	public ScreenInfo requestScreen(int width, int height) {
		if (!(scri.width == width && scri.height == height)) {
			scri.width = width;
			scri.height = height;
			scri.buf = new byte[3 * width * height];
			scri.pitch = 3 * width;
			scri.bpp = 24;
		}
		return scri;
	}

	@Override
	public void outputScreen(ScreenInfo info) {
		byte[] bgr = ((DataBufferByte) image.getRaster().getDataBuffer())
				.getData();

		for (int i = 0; i < SCREEN_WIDTH * SCREEN_HEIGHT; i++) {
			bgr[i * 3] = info.buf[i * 3 + 2];
			bgr[i * 3 + 1] = info.buf[i * 3 + 1];
			bgr[i * 3 + 2] = info.buf[i * 3 + 0];
		}

		int left = frame.getInsets().left;
		int top = frame.getInsets().top;
		Graphics g = frame.getGraphics();
		g.drawImage(image, left, top, left + SCREEN_WIDTH, top + SCREEN_HEIGHT,
				0, 0, SCREEN_WIDTH, SCREEN_HEIGHT, frame);
	}

	@Override
	public SoundInfo requestSound() {
		if (getSoundBufferState() <= 0)
			return sndi;
		else
			return null;
	}

	@Override
	public void outputSound(SoundInfo info) {
		line.write(info.buf, 0, info.sample * (info.bps / 8) * info.ch);
	}

	public int getSoundBufferState() {
		int rest = (lineBufferSize - line.available()) / (sndi.bps / 8)
				/ sndi.ch;
		if (rest < SAMPLES_PER_FRAME * BUFFER_FRAMES)
			return -1;
		if (rest == SAMPLES_PER_FRAME * BUFFER_FRAMES)
			return 0;
		return 1;
	}

	static final int[][] keyDef = {
			{ KeyEvent.VK_Z, KeyEvent.VK_X, KeyEvent.VK_SHIFT,
					KeyEvent.VK_ENTER, KeyEvent.VK_UP, KeyEvent.VK_DOWN,
					KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, },
			{ KeyEvent.VK_V, KeyEvent.VK_B, KeyEvent.VK_N, KeyEvent.VK_M,
					KeyEvent.VK_O, KeyEvent.VK_COMMA, KeyEvent.VK_K,
					KeyEvent.VK_L, } };

	private void onKey(int keyCode, boolean press) {
		for (int i = 0; i < 2; i++)
			for (int j = 0; j < 8; j++)
				if (keyCode == keyDef[i][j])
					inpi.buf[i * 8 + j] = (press ? 1 : 0);
	}

	@Override
	public InputInfo requestInput(int padCount, int buttonCount) {
		return inpi;
	}
}
