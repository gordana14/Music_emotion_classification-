package SoundAnalysis;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

import com.badlogic.audio.analysis.FFT;
import com.badlogic.audio.io.AudioDevice;
import com.badlogic.audio.io.MP3Decoder;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;

public class SongEmotionDetection extends JFrame implements GLEventListener, ActionListener {
	private GLCanvas glcanvas;
	private static final int wCanvas = 2048, hCanvas = 512;
	private int polaW = 0;
	private float sampleRate = 0;
	private JButton btnPlay;
	// private JCheckBox cbFlux = null;
	private JTextField tbLowPass = null, tbHighPass = null;
	private ArrayList<Tuple<Float, Boolean>> spectralFlux = new ArrayList<Tuple<Float, Boolean>>();
	private List<Float> spectralFluxCopy = null;
	// private double prosjekZaCijeluPjesmu = 0;
	private double maxZaCijeluPjesmu = 0;
	private int xAnimationMarker = 0;
	private int brSekZaUcitavanje = 30;
	private float minTop8PostoFlux = (float) 0;
	//private float minTop5PostoFlux = (float) 0;
	private ArrayList<PjesmaInfo> lsPjesme = null;
	private JComboBox cmbPjesme = null;
	private JRadioButton rbFlux, rbBeat = null;
	private double nUcitanBpmOrig = 0;
	private JLabel labBPM = null;
	private String s0do90 = "Sad song [0-90bpm]", s90do120 = "Moderately sad song [90-120bpm]", s120do150 = "Moderately happy song [120-150bpm]", s150Plus = "Happy song [150+ bpm]";
	private final SongEmotionDetection thisFrame = this;

	public static void main(String[] argv) throws Exception {
		try {
			javax.swing.SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					final SongEmotionDetection frame = new SongEmotionDetection();
					frame.setTitle("Song emotion detection");
					frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
					frame.setSize(frame.getContentPane().getPreferredSize());
					frame.getContentPane().setLayout(null);
					frame.setSize(new Dimension(1000, 800));
					frame.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
					frame.setVisible(true);
				}
			});
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(null, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	public SongEmotionDetection() {
		super();
		try {
			lsPjesme = new ArrayList<PjesmaInfo>();
			lsPjesme.add(new PjesmaInfo("<choose a song>", 0));
			lsPjesme.add(new PjesmaInfo("Röyksopp - This Must Be It.mp3", 122));
			lsPjesme.add(new PjesmaInfo("Bobby Vinton - Blue velvet.mp3", 80));
			lsPjesme.add(new PjesmaInfo("Billy Idol - Eyes without a face.mp3", 84));
			lsPjesme.add(new PjesmaInfo("Nipplepeople - Frka.mp3", 98));
			lsPjesme.add(new PjesmaInfo("Royksopp - Something In My Heart.mp3", 150));
			lsPjesme.add(new PjesmaInfo("Weird Al Yankovic - Polka face.mp3", 162)); 
			lsPjesme.add(new PjesmaInfo("Motorhead - Killed by death.mp3", 135));
			lsPjesme.add(new PjesmaInfo("Moody blues - Nights in white satin.mp3", 79));
			lsPjesme.add(new PjesmaInfo("Procol harum - A whiter shade of pale.mp3", 150));
			lsPjesme.add(new PjesmaInfo("Sam Cooke - Wonderful world.mp3", 129));

			// ----- Kreiranje GUI -----
			final GLProfile profile = GLProfile.get(GLProfile.GL2);
			GLCapabilities capabilities = new GLCapabilities(profile);
			glcanvas = new GLCanvas(capabilities);
			glcanvas.addGLEventListener(this); // <--- dodavanje GL display listener-a  
			glcanvas.setSize(wCanvas, hCanvas);
			this.getContentPane().add(glcanvas);

			JLabel labPjesma = new JLabel();
			labPjesma.setLocation(new Point(500, hCanvas + 20));
			labPjesma.setSize(new Dimension(40, 30));
			labPjesma.setText("Song: ");
			labPjesma.setVerticalAlignment(SwingConstants.CENTER);
			labPjesma.setHorizontalAlignment(SwingConstants.RIGHT);
			this.getContentPane().add(labPjesma);

			cmbPjesme = new JComboBox();
			for (int i = 0; i < lsPjesme.size(); i++) {
				cmbPjesme.addItem(lsPjesme.get(i).naziv);
			}
			cmbPjesme.setSelectedIndex(0);
			cmbPjesme.setLocation(new Point(labPjesma.getLocation().x + labPjesma.getSize().width + 10, hCanvas + 20));
			cmbPjesme.setSize(new Dimension(380 - labPjesma.getSize().width - 10, 30));
			cmbPjesme.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent ev) {
					try {
						ucitajIPrikaziOdabranuPjesmu();
					} catch (FileNotFoundException ex) {
						JOptionPane.showMessageDialog(thisFrame, "Selected song was not found on the file system: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
						return;
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(thisFrame, "Error while loading a song: '" + ex.getMessage() + "'.", "Error", JOptionPane.ERROR_MESSAGE);
						return;
					}
				}
			});
			this.getContentPane().add(cmbPjesme);

			labBPM = new JLabel();
			labBPM.setVerticalAlignment(SwingConstants.TOP);
			labBPM.setLocation(new Point(25, labPjesma.getLocation().y));
			labBPM.setSize(400, 200);
			popuniLabBPM_NoInfo();
			//labBPM.setBorder(BorderFactory.createLineBorder(new Color(0, 0, 0)));
			this.getContentPane().add(labBPM);

			/*
			 * cbFlux = new JCheckBox(); cbFlux.setLocation(new Point(0, hCanvas + 50)); cbFlux.setSize(220, 40); cbFlux.setText("Prikazi flux"); this.getContentPane().add(cbFlux);
			 */

			rbFlux = new JRadioButton("Show flux values");
			rbFlux.setLocation(labPjesma.getLocation().x, labPjesma.getLocation().y + labPjesma.getSize().height + 5);
			rbFlux.setSelected(false);
			rbFlux.setSize(180, 25);
			rbBeat = new JRadioButton("Show detected beats");
			rbBeat.setSize(180, 25);
			rbBeat.setLocation(labPjesma.getLocation().x, rbFlux.getLocation().y + rbFlux.getSize().height + 5);
			rbBeat.setSelected(true);
			ButtonGroup group = new ButtonGroup();
			group.add(rbFlux);
			group.add(rbBeat);
			this.getContentPane().add(rbFlux);
			this.getContentPane().add(rbBeat);
			rbFlux.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					rbBeat.setSelected(false);
					try {
						thisFrame.ucitajIPrikaziOdabranuPjesmu();
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(thisFrame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
			rbBeat.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					rbFlux.setSelected(false);
					try {
						thisFrame.ucitajIPrikaziOdabranuPjesmu();
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(thisFrame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
			this.getContentPane().add(rbFlux);
			this.getContentPane().add(rbBeat);

			JLabel labHighPass = new JLabel();
			labHighPass.setVerticalAlignment(SwingConstants.CENTER);
			labHighPass.setHorizontalAlignment(SwingConstants.RIGHT);
			labHighPass.setLocation(rbFlux.getLocation().x + rbFlux.getSize().width + 20, rbFlux.getLocation().y);
			labHighPass.setSize(80, 30);
			labHighPass.setText("High-pass:  ");
			this.getContentPane().add(labHighPass);

			tbHighPass = new JTextField();
			tbHighPass.setLocation(new Point(labHighPass.getLocation().x + labHighPass.getSize().width, labHighPass.getLocation().y));
			tbHighPass.setSize(80, 30);
			tbHighPass.setText("0");
			this.getContentPane().add(tbHighPass);

			JLabel labLowPass = new JLabel();
			labLowPass.setVerticalAlignment(SwingConstants.CENTER);
			labLowPass.setHorizontalAlignment(SwingConstants.RIGHT);
			labLowPass.setLocation(labHighPass.getLocation().x, labHighPass.getLocation().y + labHighPass.getSize().height + 10);
			labLowPass.setSize(80, 30);
			labLowPass.setText("Low-pass:  ");
			this.getContentPane().add(labLowPass);

			tbLowPass = new JTextField();
			tbLowPass.setLocation(new Point(labLowPass.getLocation().x + labLowPass.getSize().width, labLowPass.getLocation().y));
			tbLowPass.setSize(80, 30);
			tbLowPass.setText("30000");
			this.getContentPane().add(tbLowPass);

			JButton btnUcitaj = new JButton();
			btnUcitaj.setSize(new Dimension(tbLowPass.getSize().width, 30));
			btnUcitaj.setText("Load");
			btnUcitaj.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent arg0) {
					try {
						ucitajIPrikaziOdabranuPjesmu();
					} catch (Exception ex) {
						JOptionPane.showMessageDialog(thisFrame, "Error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
					}
				}
			});
			// btnPlay.setLocation(new Point(cmbPjesme.getLocation().x + cmbPjesme.getSize().width - btnPlay.getSize().width, cmbPjesme.getLocation().y + cmbPjesme.getSize().height + 20));
			btnUcitaj.setLocation(new Point(tbLowPass.getLocation().x, tbLowPass.getLocation().y + tbLowPass.getSize().height + 10));
			this.getContentPane().add(btnUcitaj);

			btnPlay = new JButton();
			btnPlay.setSize(new Dimension(100, 30));
			btnPlay.setText("Play");
			btnPlay.addActionListener(this);
			// btnPlay.setLocation(new Point(cmbPjesme.getLocation().x + cmbPjesme.getSize().width - btnPlay.getSize().width, cmbPjesme.getLocation().y + cmbPjesme.getSize().height + 20));
			btnPlay.setLocation(new Point(cmbPjesme.getLocation().x + cmbPjesme.getSize().width + 10, cmbPjesme.getLocation().y));
			this.getContentPane().add(btnPlay);

			
			ucitajIPrikaziOdabranuPjesmu();
			/*
			 * btnUcitaj = new JButton(); btnUcitaj.setSize(new Dimension(100, 50)); btnUcitaj.setText("Ucitaj"); btnUcitaj.addActionListener(this); btnUcitaj.setLocation(new
			 * Point(btnPlay.getLocation().x, btnPlay.getLocation().y + btnPlay.getSize().height)); this.getContentPane().add(btnUcitaj);
			 */

			// ---------------------------------------------

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(thisFrame, "Error in constructor: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}


	private float[] lowHighPass(float[] spectrum, float hzLowPass, float hzHighPass) {

        float[] spectrumFiltered = new float[spectrum.length];

        for (int i = 0; i < spectrum.length; i++) {

              double hz = (double) (i * sampleRate) / (double) 1024;

              if (hz <= hzLowPass && hz >= hzHighPass){

                    spectrumFiltered[i] = spectrum[i];

              }else{

                    spectrumFiltered[i] = 0;

              }

        }

        return spectrumFiltered;

  }
	
	private void popuniLabBPM_NoInfo(){
		labBPM.setText("<html><table border='1' style='width:300px'>"+
		"<tr><td style='font-weight:900;text-align:center;font-size:10px'>Information about the loaded song</td></tr>"+
		"<tr><td>&lt;Song is not loaded&gt;</td></tr></table></html>");
	}

	private void ucitajIPrikaziOdabranuPjesmu() throws Exception {
		int iSel = cmbPjesme.getSelectedIndex();
		String fileName = lsPjesme.get(iSel).naziv;
		if (fileName.startsWith("<")){
			popuniLabBPM_NoInfo();
			return;
		}
		nUcitanBpmOrig = lsPjesme.get(iSel).bpmOrig;
		String filePath = "samples\\" + fileName;
		thisFrame.setTitle("Song tempo and emotion detection for \"" + fileName + "\"");

		String sLowPass = tbLowPass.getText().trim();
		String sHighPass = tbHighPass.getText().trim();
		int hzLowPass = 0, hzHighPass = 30000;
		try {
			hzLowPass = Integer.parseInt(sLowPass);
			hzHighPass = Integer.parseInt(sHighPass);
			if (hzLowPass < 0)
				throw new Exception("");
			if (hzHighPass < 0)
				throw new Exception("");
		} catch (Exception ex) {
			throw new Exception("Fields 'Low pass' and 'High pass' must be filled by non-negative whole numbers.");
		}

		MP3Decoder decoder = null;
		try {
			decoder = new MP3Decoder(new FileInputStream(filePath));
		} catch (FileNotFoundException ex) {
			throw new Exception("Selected song '" + filePath + "' was not found. Error: " + ex.getMessage());
		} catch (Exception ex) {
			throw new Exception("Error while loading the MP3 song: " + ex.getMessage());
		}

		sampleRate = decoder.sampleRate;
		FFT fft = new FFT(1024, sampleRate);
		polaW = wCanvas / 2;

		float[] samples = new float[1024];
		float[] spectrum = new float[1024 / 2 + 1];
		float[] lastSpectrum = new float[1024 / 2 + 1];
		spectralFlux = new ArrayList<Tuple<Float, Boolean>>();

		long brojac = 0;
		maxZaCijeluPjesmu = 0;
		// prosjekZaCijeluPjesmu = 0;

		double br1024BlokPoSek = (double) sampleRate / (double) 1024;
		//detektovanje 8 posto najjacih flux-ova (Sound energy alg) 
		while (decoder.readSamples(samples) > 0) {
			fft.forward(samples);
			
			System.arraycopy(spectrum, 0, lastSpectrum, 0, spectrum.length);
			System.arraycopy(fft.getSpectrum(), 0, spectrum, 0, spectrum.length);
			spectrum = lowHighPass(spectrum, hzLowPass, hzHighPass);
			/*
			  for(int i=0; i<spectrum.length; i++){ System.out.println("Spectrum elementi" +spectrum[i]); }
			 */

			float flux = 0;
			for (int i = 0; i < spectrum.length; i++) {
				float value = (spectrum[i] - lastSpectrum[i]);
				flux += value < 0 ? 0 : value;
			}
			if (maxZaCijeluPjesmu < flux)
				maxZaCijeluPjesmu = flux;
			Tuple<Float, Boolean> tuple = new Tuple<Float, Boolean>(flux, false);
			spectralFlux.add(tuple);

			
			// prosjekZaCijeluPjesmu += (flux < 0 ? 0 : flux);
			brojac++;
			if (brojac > (int) (brSekZaUcitavanje * br1024BlokPoSek))
				break;
		}
	 
	//	for (int i =0 ; i<spectralFlux.size();i++) {System.out.println("Spectrum flux final" + spectralFlux.get(i).vrh.toString()+ spectralFlux.get(i).x.toString());}
		// prosjekZaCijeluPjesmu = prosjekZaCijeluPjesmu / (double) brojac;
		spectralFluxCopy = new ArrayList<Float>();
		for (int iF = 0; iF < spectralFlux.size(); iF++) {
			spectralFluxCopy.add(spectralFlux.get(iF).x);
		}
		// --- sortiranje flux-a i uzimanje minimalnog fluxa koji ulazi u top 8 posto ---
		mergeSort((ArrayList) spectralFluxCopy);
		int top8Posto = (int) (0.08 * (double) spectralFluxCopy.size());
		//int top5Posto = (int) (0.05 * (double) spectralFluxCopy.size());
		minTop8PostoFlux = spectralFluxCopy.get(spectralFluxCopy.size() - top8Posto);
		//minTop5PostoFlux = spectralFluxCopy.get(spectralFluxCopy.size() - top5Posto);

		for (int i = 0; i < spectralFlux.size(); i++) {
			if (spectralFlux.get(i).x >= minTop8PostoFlux)
				spectralFlux.get(i).vrh = true;
			else
				spectralFlux.get(i).vrh = false;
		}
		pronadjiMaxZajedDjel(spectralFlux);
		glcanvas.display();

		/*
		 * for (int i = spectralFluxCopy.size() - top8Posto; i >= 0; i--) { spectralFluxCopy.remove(i); }
		 */

		/*
		 * for (int i=0; i<spectralFluxCopy.size(); i++){ System.out.println(spectralFluxCopy.get(i)); }
		 */
	}

	private ArrayList pronadjiMaxZajedDjel(ArrayList<Tuple<Float, Boolean>> ls0) {
		ArrayList<Integer> lsIndeksiVrhova = new ArrayList<Integer>();
		for (int i = 0; i < ls0.size(); i++) {
			/*
			 * lsTemp.get(i).x = ls0.get(i).x; lsTemp.get(i).y = ls0.get(i).y;
			 */
			if (ls0.get(i).vrh == true)
				lsIndeksiVrhova.add(i);
			
			
		}
	//	 for (int i=0; i<lsIndeksiVrhova.size(); i++){ System.out.println(lsIndeksiVrhova.get(i)); }
		int deltaBest = -1;
		int pogodjenihBest = 0, promasenihBest = 0, sumaPromAndPogBest = 0;
		int iKorekcijaProsjekBest = 0;
		// double uspjesnostBest = 0;
		// ----- Trazenje najboljeg pocetnog beat-a -----
		for (int i0 = 0; i0 <= -2 + 3 * lsIndeksiVrhova.size() / 4; i0++) {

			// for (int i0 = 0; i0 <= 20; i0++) {
			/*
			 * if (lsIndeksiVrhova.get(i0)<minTop5PostoFlux) continue;
			 */

			// ----- Trazenje najboljeg zavrsnog beat-a u taktu (najvise 3 (top 5%) fluxeva udaljen) -----
			for (int i1 = i0 + 1; i1 < i0 + 4; i1++) {
				int deltaTemp = lsIndeksiVrhova.get(i1) - lsIndeksiVrhova.get(i0);
				int opsegGreske = deltaTemp / 4;
				int iKorekcijaProsjekTemp = 0;
				int pogodjenihTemp = 0, promasenihTemp = 0;
				int x1 = lsIndeksiVrhova.get(i1);
				for (int iB = i1 + 1; iB < ls0.size(); iB++) {
					ls0.get(iB).NaglasiTemp = false;
				}
				ls0.get(lsIndeksiVrhova.get(i0)).NaglasiTemp = true;
				ls0.get(lsIndeksiVrhova.get(i1)).NaglasiTemp = true;

				int brojacBeata = 0;
				// ----- Zbog neprecizne rezolucije fluxeva, trazi zavrsni beat u siroj lokaciji oko pretpostavljene lokacije zavrsnog beat-a -----
				while (x1 + deltaTemp + opsegGreske < ls0.size()) {
					boolean bPogodjenVrh = false;
					int i2;
					for (i2 = 0; i2 <= opsegGreske; i2++) {
						if (ls0.get(x1 + deltaTemp + i2).vrh == true) {
							
							x1 = x1 + deltaTemp + i2;
							iKorekcijaProsjekTemp += i2;
							bPogodjenVrh = true;
							break;
						}
						if (ls0.get(x1 + deltaTemp - i2).vrh == true) {
							x1 = x1 + deltaTemp - i2;
							iKorekcijaProsjekTemp -= i2;
							bPogodjenVrh = true;
							break;
						}
					}

					if (bPogodjenVrh) {
						pogodjenihTemp++;
						ls0.get(x1).NaglasiTemp = true;
					} else {
						promasenihTemp++;
						x1 = x1 + deltaTemp;
						ls0.get(x1).NaglasiTemp = false;
					}
					brojacBeata++;
				}
				
				iKorekcijaProsjekTemp = iKorekcijaProsjekTemp / brojacBeata; // <--- pamti se prosjecna korekcija beat-a da bi se na kraju dodala na konacni rezultat bpm

				int sumaPromAndPogTemp = pogodjenihTemp + promasenihTemp;
				double uspjesnostTemp = (double) ((double) pogodjenihTemp / (double) sumaPromAndPogTemp);

				// --- Konacna analiza uspjesnosti pretpostavljenog beat-a u odnosu na najbolji do sada ---
				if (uspjesnostTemp > 0.7 && pogodjenihTemp >= 1.5 * pogodjenihBest) {
					// xBest = i1;
					deltaBest = deltaTemp;
					pogodjenihBest = pogodjenihTemp;
					promasenihBest = promasenihTemp;
					sumaPromAndPogBest = pogodjenihBest + promasenihBest;
					iKorekcijaProsjekBest = iKorekcijaProsjekTemp;
					// uspjesnostBest=(double) pogodjenihBest / (double) sumaPromAndPogBest;
					for (int iN = 0; iN < ls0.size(); iN++) {
						ls0.get(iN).Naglasi = ls0.get(iN).NaglasiTemp;
					}
				}
			}
		}
		NumberFormat formatter = new DecimalFormat("#0.00");
		double BPM = (double) 60.0 / (double) (1024 * (double) (deltaBest + iKorekcijaProsjekBest) / (double) sampleRate);
		/*
		 * labBPM.setText("<html>BPM online = " + nUcitanBpmOrig + "<br>BPM detektovani = " + formatter.format(BPM) + " [pogodjenih=" + pogodjenihBest + "/" + sumaPromAndPogBest + "]<br>" +
		 * "<strong><u>"+vratiEmocijuPjesme(BPM)+"</u></strong></html>");
		 */
		labBPM.setText("<html><table style='width:300px' border='1'><tr><td colspan=2 style='text-align:center;font-size:10px'>Information about the loaded song</td></tr>"
				+ "<tr><td style='text-align:right;width:35%'>BPM - detected:</td><td>" + formatter.format(BPM) + "</td></tr>"
				+ "<tr><td style='text-align:right;width:35%'>BPM - online source:</td><td>" + nUcitanBpmOrig + "</td></tr>"
				+ "<tr><td style='text-align:right;width:35%'>Song emotion:</td><td style='color:#000000;font-weight:900'>"
				+ vratiEmocijuPjesme(BPM) + "</td></tr></body></html>");

		return null;
	}

	private String vratiEmocijuPjesme(double bpm) {
		if (0 < bpm && bpm <= 90)
			return s0do90;
		if (90 < bpm && bpm <= 120)
			return s90do120;
		if (120 < bpm && bpm <= 150)
			return s120do150;
		if (150 < bpm)
			return s150Plus;
		return "Undefined emotion";
	}

	private class Tuple<X, Y> {
		public X x;
		public Y vrh;
		public boolean NaglasiTemp = false;
		public boolean Naglasi = false;

		public Tuple(X x, Y y) {
			this.x = x;
			this.vrh = y;
		}
	}

	private static void mergeSort(ArrayList<Float> a) {
		if (a.size() <= 1)
			return; // small list don't need to be merged

		// SEPARATE
		int mid = a.size() / 2; // estimate half the size

		ArrayList<Float> left = new ArrayList<Float>();
		ArrayList<Float> right = new ArrayList<Float>();

		for (int i = 0; i < mid; i++)
			left.add(a.remove(0)); // put first half part in left
		while (a.size() != 0)
			right.add(a.remove(0)); // put the remainings in right
		// Here a is now empty

		// MERGE PARTS INDEPENDANTLY

		mergeSort(left); // merge the left part
		mergeSort(right); // merge the right part

		// MERGE PARTS

		// while there is something in the two lists
		while (left.size() != 0 && right.size() != 0) {
			// compare both heads, add the lesser into the result and remove it from its list
			if (left.get(0).compareTo(right.get(0)) < 0)
				a.add(left.remove(0));
			else
				a.add(right.remove(0));
		}

		// fill the result with what remains in left OR right (both can't contains elements)
		while (left.size() != 0)
			a.add(left.remove(0));
		while (right.size() != 0)
			a.add(right.remove(0));
	}

	@Override
	public void actionPerformed(ActionEvent arg0) {
		String fileName = (String) cmbPjesme.getSelectedItem();
		String fullPath = "samples\\" + fileName;
		try{
			svirajPjesmu(fullPath);
		}catch(Exception ex){
			JOptionPane.showMessageDialog(thisFrame, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}

	@Override
	public void display(GLAutoDrawable drawable) {
		/*
		 * float msPoPix = 1000*(float)1024/(float)sampleRate; float msProteklo = periodTimer*brUlazaUTimer; int xMarker = (int)(msProteklo/msPoPix);
		 */
		xAnimationMarker += 1;
		final GL2 gl = drawable.getGL().getGL2();
		gl.glColor3f(0.0f, 1.0f, 0.0f);
		gl.glClear(GL4.GL_COLOR_BUFFER_BIT | GL4.GL_DEPTH_BUFFER_BIT);
		// polaW = spectralFlux.size();
		float kx = (float) 1 / (float) polaW;
		float ky = (float) 0.5 / (float) maxZaCijeluPjesmu;

		for (int i = 1; i < spectralFlux.size(); i++) {
			float valPret = spectralFlux.get(i - 1).x;
			float valTren = spectralFlux.get(i).x;

			// boolean bZacrveni = false;
			gl.glColor3f(0.0f, 1.0f, 0.0f);
			if (rbFlux.isSelected()) {
				if (spectralFlux.get(i - 1).vrh || spectralFlux.get(i).vrh) {
					// bZacrveni = ; // spectralFluxCopy.contains(valPret) || spectralFluxCopy.contains(valTren);
					gl.glColor3f(1.0f, 0.0f, 0.0f);
				}
			} else {
				if (spectralFlux.get(i - 1).Naglasi || spectralFlux.get(i).Naglasi) {
					int iNaglasi = 0;
					if (spectralFlux.get(i - 1).Naglasi)
						iNaglasi = i - 1;
					else
						iNaglasi = i;
					// bZacrveni = spectralFlux.get(i - 1).vrh || spectralFlux.get(i).vrh; // spectralFluxCopy.contains(valPret) || spectralFluxCopy.contains(valTren);
					gl.glColor3f(0.5f, 0.2f, 0.0f);
					gl.glBegin(GL2.GL_LINES);
					gl.glVertex3f((iNaglasi - polaW) * kx, 1, 0);
					gl.glVertex3f((iNaglasi - polaW) * kx, -1, 0);
					gl.glEnd();
					gl.glColor3f(0.0f, 1.0f, 0.0f);
				}
			}

			/*
			 * if (bZacrveni) gl.glColor3f(1.0f, 0.0f, 0.0f); else gl.glColor3f(0.0f, 1.0f, 0.0f);
			 */

			gl.glBegin(GL2.GL_LINES);
			gl.glVertex3f((i - 1 - polaW) * kx, valPret * ky, 0);
			gl.glVertex3f((i - polaW) * kx, valTren * ky, 0);
			gl.glEnd();
		}
		gl.glColor3f(1.0f, 1.0f, 1.0f);
		gl.glBegin(GL2.GL_LINES);
		gl.glVertex3f((xAnimationMarker - polaW) * kx, -1, 0);
		gl.glVertex3f((xAnimationMarker - polaW) * kx, 1, 0);
		gl.glEnd();

		gl.glFlush();
	}

	private void svirajPjesmu(String filePath) throws Exception{
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(filePath);
			MP3Decoder decoder = new MP3Decoder(fis);
			AudioDevice device = new AudioDevice();
			float[] samples = new float[1024];

			long startTime = 0;
			int brojac = 0;
			long samplesUcitano = 0;
			while ((samplesUcitano += decoder.readSamples(samples)) > 0) {
				if (samplesUcitano > brSekZaUcitavanje * sampleRate)
					break;
				device.writeSamples(samples);
				/*
				 * xMarker+=1; if (xMarker%10==0) glcanvas.display();
				 */

				if (startTime == 0)
					startTime = System.nanoTime();
				float elapsedTime = (System.nanoTime() - startTime) / 1000000000.0f;
				int position = (int) (elapsedTime * (sampleRate / 1024));
				xAnimationMarker = position;
				brojac = (brojac + 1) % 10; // <--- otprilike 5x po sekundi se pomakne bijela linija
				if (brojac == 0) {
					glcanvas.display();
				}
			}
		} catch (Exception ex) {
			throw new Exception("Error while playing a song: " + ex.getMessage());
		} finally {
			try {
				fis.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void dispose(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void init(GLAutoDrawable arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void reshape(GLAutoDrawable arg0, int arg1, int arg2, int arg3, int arg4) {
		// TODO Auto-generated method stub

	}

	class PjesmaInfo {
		String naziv;
		double bpmOrig;

		public PjesmaInfo(String p_naziv, double p_bpmOrig) {
			this.naziv = p_naziv;
			this.bpmOrig = p_bpmOrig;
		}

	}

}
