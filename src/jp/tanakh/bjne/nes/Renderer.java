package jp.tanakh.bjne.nes;

public interface Renderer {
	public class ScreenInfo {
		public byte[] buf;
		public int width;
		public int height;
		public int pitch;
		public int bpp;
	}

	public class SoundInfo {
		public byte[] buf;
		public int freq;
		public int bps;
		public int ch;
		public int sample;
	}

	public class InputInfo {
		public int[] buf;
	}

	public ScreenInfo requestScreen(int width, int height);

	public SoundInfo requestSound();

	public InputInfo requestInput(int padCount, int buttonCount);

	public void outputScreen(ScreenInfo info);

	public void outputSound(SoundInfo info);

	public void outputMessage(String msg);
}
