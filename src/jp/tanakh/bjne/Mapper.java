package jp.tanakh.bjne;

import jp.tanakh.bjne.Renderer.SoundInfo;

public interface Mapper {
	void write(short adr, byte dat);

	void hblank(int line);

	void audio(SoundInfo info);

	// void serialize(StateData sd){}
}
