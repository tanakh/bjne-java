package jp.tanakh.bjne.nes;

import jp.tanakh.bjne.nes.Renderer.SoundInfo;

public interface Mapper {
	void write(short adr, byte dat);

	void hblank(int line);

	void audio(SoundInfo info);

	// void serialize(StateData sd){}
}
