package jp.tanakh.bjne;

import jp.tanakh.bjne.mapper.NullMapper;

public class MapperMaker {
	static Mapper makeMapper(int num, Nes n) {
		switch (num) {
		case 0:
			return new NullMapper();

		default:
			return null;
		}
	}
}
