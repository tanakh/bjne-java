package jp.tanakh.bjne.ui;

import java.util.Date;

public class FPSTimer {
	FPSTimer() {
		reset();
	}

	public void reset() {
		bef = getTime();
		timing = 0;
	}

	public boolean elapse(double fps) {
		double spf = 1.0 / fps;
		timing += getElapsed() - spf;
		if (timing > 0) {
			if (timing > spf) {
				reset();
				return true;
			}
			return false;
		} else {
			try {
				Thread.sleep((long) (-timing * 1000));
			} catch (InterruptedException e) {
			}
			return true;
		}
	}

	private double getElapsed() {
		double cur = getTime();
		double ret = cur - bef;
		bef = cur;
		return ret;
	}

	private double getTime() {
		return new Date().getTime() / 1000.0;
	}

	private double bef = 0;
	private double timing = 0;
}
