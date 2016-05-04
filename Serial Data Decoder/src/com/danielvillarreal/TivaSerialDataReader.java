package com.danielvillarreal;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.commons.codec.binary.Hex;
import org.jfree.data.time.Millisecond;

public class TivaSerialDataReader{

	private DynamicDataDemo demo;
	String port1 = "/dev/tty.HC-06-DevB";
	String port2 = "/dev/tty.SLAB_USBtoUART";
	PrintWriter writer;
	PrintWriter full;

	public TivaSerialDataReader(){
		//demo = new DynamicDataDemo("TIVA Demo");
		//demo.pack();
		//RefineryUtilities.centerFrameOnScreen(demo);
		//demo.setVisible(true);

		try {
			writer = new PrintWriter("errorDetectionOutput.txt");
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownSaver(writer)));
		attempConnection(port1);
	
	}
	
	void attempConnection(String port){
		try {
			this.connect(port);
			System.out.println("Connected to port!");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Retrying...");
			attempConnection(port);
		}
		
		
	}

	void connect ( String portName ) throws Exception{
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
		if ( portIdentifier.isCurrentlyOwned() ){
			System.out.println("Error: Port is currently in use");
		}
		else{
			CommPort commPort = portIdentifier.open(this.getClass().getName(),5000);


			if (commPort instanceof SerialPort){
				SerialPort serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(921600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);



				InputStream in = serialPort.getInputStream();
				BufferedInputStream bis = new BufferedInputStream(in);


				(new Thread(new SerialReader(in,bis))).start();


			}
			else{
				System.out.println("Error: Only serial ports are handled by this example.");
			}
		}     
	}

	public class ShutdownSaver implements Runnable{

		PrintWriter wr;

		public ShutdownSaver(PrintWriter wr) {
			// TODO Auto-generated constructor stub
			this.wr = wr;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			wr.close();
			wr.flush();
			System.out.println("closed file");

		}

	}

	public class DataSetAdder implements Runnable{

		ArrayList<Sample> toAdd;
		DynamicDataDemo plot;

		public DataSetAdder(ArrayList<Sample> data, DynamicDataDemo plot){
			this.toAdd = data;
			this.plot = plot;
		}

		@Override
		public void run() {
			int count = toAdd.size();
			for(Sample s: toAdd){
				Millisecond now = new Millisecond();
				if(count-- == 1){
					plot.addNewSample(s, now,true);
				}else{
					plot.addNewSample(s, now,false);
				}

			}
			demo.updateChart();

		}

	}


	public class SerialReader implements Runnable {
		InputStream in;
		BufferedInputStream bis;

		public SerialReader ( InputStream in , BufferedInputStream bis){
			this.in = in;
			this.bis = bis;
		}

		public void run ()
		{
			byte[] buffer = new byte[2];
			int len = -1;
			int currentId = -1;
			int countSinceError = 0;
			int errorCount = 0;
			byte temp[];


			try{

				while(!(bis.available() >= 2)){
					//Wait until there is enough data
				}
				
				boolean initialError = true;

				while(initialError){
					bis.mark(5);
					String firstSample = Hex.encodeHexString(readFromStream(this.bis, 2));
					int temporaryId = extractId(2, firstSample);

					if(detectError(Hex.encodeHexString(readFromStream(this.bis, 2)), temporaryId+1)){
						//Error, sequence off
						bis.reset();
						//Correct sequence by extracting two words
						readFromStream(bis, 1);
						writer.println("Sequnece off. Adjusted!");
					}else{
						initialError = false;
						bis.reset();
					}
				}


				while(in.available() > 2){

					temp = new byte[2];
					bis.read(temp);
					String newSample = Hex.encodeHexString(temp);

					if(currentId == -1){
						currentId = extractId(2, newSample);
					}
					//System.out.println("New sample: " + newSample);

					if(extractId(2,newSample) != currentId){


						bis.mark(3);

						//Error id mismatch
						byte discard[] = new byte[2];
						bis.read(discard);

						String checkSample = Hex.encodeHexString(discard);
						if(extractId(2,checkSample) != (currentId+1)){
							bis.reset();
							currentId--;
						}

						//System.out.println("Discard: " + Hex.encodeHexString(discard));
						String debug = ("error-" + currentId + " cse: " + countSinceError + " sample: " + newSample);
						writer.println(debug);
						//System.out.println(debug);
						//in.skip(1);
						//System.out.println("Error detected!");
						//System.out.println("Err-Sample,currentId: " + newSample + " ," + currentId );
						currentId += 2;
						countSinceError = 0;
						errorCount++;
					}else{
						//System.out.println("OK Smp: " + newSample);
						writer.println(newSample);
						currentId++;
						countSinceError++;
					}

					if(currentId > 15){
						currentId = 0;
					}

					//System.out.println(Hex.encodeHexString(temp));
				}

			}catch ( IOException e ){
				e.printStackTrace();
			}

		}

		private boolean detectError(String sample, int currentId){
			return extractId(2,sample) != currentId;
		}

		private void checkBlockTransferReady(ArrayList<Sample> data,int blockSize) {
			if(data.size() >= blockSize){
				startBlockTranfer(data,blockSize);
			}

		}

		private void startBlockTranfer(ArrayList<Sample> data,int blockSize){
			//new Thread(new DataSetAdder(data, demo)).start();
			(new DataSetAdder(data,demo)).run();
		}
	}

	private void addSetToChart(DynamicDataDemo plot, ArrayList<Sample> data){

		for(Sample s : data){
			Millisecond now = new Millisecond();
			plot.addNewSample(s, now, true);
		}
	}



	private Sample rawStringToSample(String rawDataSample, int idPosition){
		return (new Sample(extractData(idPosition,rawDataSample),extractId(idPosition,rawDataSample)));
	}

	private byte[] readFromStream(BufferedInputStream bis, int numberOfBytes){
		byte result[] = new byte[numberOfBytes];
		try {
			if(bis.available() >= numberOfBytes){
				bis.read(result);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	private  int extractId(int idPosition, String data){
		return Integer.parseInt(extractIdHex(idPosition,data),16);
	}
	private String extractIdHex(int idPosition, String data){
		return data.charAt(idPosition) + "";
	}

	private String extractDataHex(int idPosition, String data){
		//System.out.println("DBG: extractDataHex" + idPosition);
		String orderedData = new String();
		if(idPosition == 2){
			orderedData = ("" + data.charAt(3) + data.charAt(0) + data.charAt(1));
		}else if(idPosition == 0){
			orderedData = ("" + data.charAt(1) + data.charAt(2) + data.charAt(3));
		}

		return orderedData;
	}

	private int extractData(int idPosition, String data){

		return Integer.parseInt(extractDataHex(idPosition,data),16);
	}

	private static int findIdPosition(String sampleOne, String sampleTwo){
		byte temp[] = new byte[2];

		char samplePosThree[] = new char[2];
		char samplePosOne[] = new char[2];

		samplePosThree[0] = sampleOne.charAt(0);
		samplePosThree[1] = sampleTwo.charAt(0);

		samplePosOne[0] = sampleOne.charAt(2);
		samplePosOne[1] = sampleTwo.charAt(2);

		//Check whether position three contains the id

		boolean decision[] = new boolean[2];
		int posThreeValue[] = new int[2];
		int posOneValue[] = new int[2];

		for(int i = 0; i < 2; i++){
			posThreeValue[i] = Integer.parseInt(samplePosThree[i] + "",16);
			posOneValue[i] = Integer.parseInt(samplePosOne[i] + "",16);

		}
		decision[0] = checkSequence(posThreeValue[0],posThreeValue[1]);
		decision[1] = checkSequence(posOneValue[0],posOneValue[1]);

		if(!(decision[0]|decision[1])){
			System.out.println("DBG: SampleOne: " + sampleOne + " SampleTwo: " + sampleTwo);
		}

		if(decision[0]){
			return 0;
		}else if(decision[1]){
			return 2;
		}else return -1;

	}

	private static boolean checkSequence(int a, int b){

		if(a == b){
			return false;
		}

		if(a > b){
			return true;
		}else if(b > a){
			return true;
		}else return false;

	}



	public static void main ( String[] args){
		new TivaSerialDataReader();
	}
}