package jp.tanakh.bjne.nes;

import jp.tanakh.bjne.nes.Renderer.SoundInfo;

public abstract class MapperAdapter implements Mapper {

	@Override
	public void audio(SoundInfo info) {
	}

	@Override
	public void hblank(int line) {
	}

	@Override
	public void reset() {
	}

	@Override
	public void write(short adr, byte dat) {
	}

}
