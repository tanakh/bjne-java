package jp.tanakh.bjne.nes.mapper;

import jp.tanakh.bjne.nes.MapperAdapter;
import jp.tanakh.bjne.nes.Nes;
import jp.tanakh.bjne.nes.Ppu;

public class MMC1 extends MapperAdapter {
	public MMC1(Nes n) {
		nes = n;
		reset();
	}

	@Override
	public int mapperNo() {
		return 1;
	}

	@Override
	public void reset() {
		romSize = nes.getRom().romSize();
		sizeInKB = romSize * 16;

		cnt = buf = 0;
		mirror = false;
		oneScreen = false;
		switchArea = true;
		switchSize = true;
		vromSwitchSize = false;
		swapBase = 0;
		vromPage[0] = vromPage[1] = 1;
		romPage[0] = 0;
		romPage[1] = romSize - 1;

		setBank();
	}
	
	private static boolean _bit(int x, int n) {
		return ((x >> n) & 1) != 0;
	}
	
	@Override
	public void write(short sadr, byte sdat){
		int adr = sadr & 0xffff;
		int dat = sdat & 0xff;

	    if ((dat&0x80)!=0){
	        cnt=0;buf=0;
	        return;
	      }
	      buf|=((dat&1)<<cnt);
	      cnt++;
	      if (cnt<5) return;
	      dat=buf&0xff;
	      buf=cnt=0;
	      
	      if (adr>=0x8000&&adr<=0x9FFF){ // reg0
	        mirror =_bit(dat,0);
	        oneScreen =_bit(dat,1);
	        switchArea =_bit(dat,2);
	        switchSize =_bit(dat,3);
	        vromSwitchSize=_bit(dat,4);

	        setMirroring();
	      }
	      else if (adr>=0xA000&&adr<=0xBFFF){ // reg1
	        if (sizeInKB==512){
	          swapBase=_bit(dat,4)?16:0;
	          setBank();
	        }
	        else if (sizeInKB==1024){
	        }

	        vromPage[0]=dat&0xf;
	        if (vromSwitchSize){
	          nes.getMbc().mapVrom(0,vromPage[0]*4);
	          nes.getMbc().mapVrom(1,vromPage[0]*4+1);
	          nes.getMbc().mapVrom(2,vromPage[0]*4+2);
	          nes.getMbc().mapVrom(3,vromPage[0]*4+3);
	        }
	        else{
	          nes.getMbc().mapVrom(0,vromPage[0]*4);
	          nes.getMbc().mapVrom(1,vromPage[0]*4+1);
	          nes.getMbc().mapVrom(2,vromPage[0]*4+2);
	          nes.getMbc().mapVrom(3,vromPage[0]*4+3);
	          nes.getMbc().mapVrom(4,vromPage[0]*4+4);
	          nes.getMbc().mapVrom(5,vromPage[0]*4+5);
	          nes.getMbc().mapVrom(6,vromPage[0]*4+6);
	          nes.getMbc().mapVrom(7,vromPage[0]*4+7);
	        }
	      }
	      else if (adr>=0xC000&&adr<=0xDFFF){ // reg2
	        vromPage[1]=dat&0xf;
	        if (vromSwitchSize){
	          nes.getMbc().mapVrom(4,vromPage[1]*4);
	          nes.getMbc().mapVrom(5,vromPage[1]*4+1);
	          nes.getMbc().mapVrom(6,vromPage[1]*4+2);
	          nes.getMbc().mapVrom(7,vromPage[1]*4+3);
	        }

	        if (sizeInKB==1024){
	        }
	      }
	      else if (adr>=0xE000){ // reg3
	        if (switchSize){ // 16K
	          if (switchArea){
	            romPage[0]=dat&0xf;
	            romPage[1]=(romSize-1)&0xf;
	          }
	          else{
	            romPage[0]=0;
	            romPage[1]=dat&0xf;
	          }
	        }
	        else{ // 32K
	          romPage[0]=dat&0xe;
	          romPage[1]=(dat&0xe)|1;
	        }
	        setBank();
	      } 
	}

	private void setBank() {
		nes.getMbc().mapRom(0, (swapBase | romPage[0]) * 2);
		nes.getMbc().mapRom(1, (swapBase | romPage[0]) * 2 + 1);
		nes.getMbc().mapRom(2, (swapBase | romPage[1]) * 2);
		nes.getMbc().mapRom(3, (swapBase | romPage[1]) * 2 + 1);
	}

	private void setMirroring() {
		if (oneScreen)
			nes.getPpu().setMirroring(
					mirror ? Ppu.MirrorType.HOLIZONTAL
							: Ppu.MirrorType.VERTICAL);
		else if (mirror)
			nes.getPpu().setMirroring(1, 1, 1, 1);
		else
			nes.getPpu().setMirroring(0, 0, 0, 0);
	}

	int romSize, sizeInKB;
	int cnt, buf;
	boolean mirror, oneScreen, switchArea, switchSize, vromSwitchSize;
	int swapBase;
	int[] vromPage = new int[2];
	int[] romPage = new int[2];

	private Nes nes;
}
