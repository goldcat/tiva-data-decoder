package com.danielvillarreal;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.FileWriter;
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

		try{
			this.connect(port1);
			System.out.println("Connected to port!");
			writer = new PrintWriter("correctedOutput.txt");
			full = new PrintWriter("fullOut.txt");
		}
		catch ( Exception e ){
			e.printStackTrace();
		}
	}

	void connect ( String portName ) throws Exception{
		CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(portName);
		if ( portIdentifier.isCurrentlyOwned() ){
			System.out.println("Error: Port is currently in use");
		}
		else{
			CommPort commPort = portIdentifier.open(this.getClass().getName(),2000);


			if (commPort instanceof SerialPort){
				SerialPort serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(921600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);



				InputStream in = serialPort.getInputStream();


				(new Thread(new SerialReader(in))).start();


			}
			else{
				System.out.println("Error: Only serial ports are handled by this example.");
			}
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

		public SerialReader ( InputStream in ){
			this.in = in;
		}

		public void run ()
		{
			byte[] buffer = new byte[2];
			int len = -1;
			int currentId = -1;
			int countSinceError = 0;
			int errorCount = 0;

			try{

					while(in.available() > 2){
						byte temp[] = new byte[2];
						in.read(temp);
						
						String newSample = Hex.encodeHexString(temp);
						
						if(currentId == -1){
							currentId = extractId(2, newSample);
						}
						System.out.println("New sample: " + newSample);
						
						if(extractId(2,newSample) != currentId){
							//Error id mismatch
							byte discard[] = new byte[1];
							in.read(discard);
							System.out.println("Discard: " + Hex.encodeHexString(discard));
							String debug = ("error-" + currentId + " cse: " + countSinceError + " sample: " + newSample);
							writer.println(debug);
							System.out.println(debug);
							//in.skip(1);
							//System.out.println("Error detected!");
							//System.out.println("Err-Sample,currentId: " + newSample + " ," + currentId );
							currentId += 2;
							countSinceError = 0;
							errorCount++;
						}else{
							System.out.println("OK Smp: " + newSample);
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

		private Sample readNextSample(InputStream in, int numberOfBytes, int idPosition){
			byte data[] = readFromStream(in,numberOfBytes);
			String resultString = Hex.encodeHexString(data);
			return (new Sample(extractData(idPosition,resultString),extractId(idPosition,resultString)));
		}

		private Sample rawStringToSample(String rawDataSample, int idPosition){
			return (new Sample(extractData(idPosition,rawDataSample),extractId(idPosition,rawDataSample)));
		}

		private byte[] readFromStream(InputStream in, int numberOfBytes){
			byte result[] = new byte[numberOfBytes];
			try {
				if(in.available() >= numberOfBytes){
					in.read(result);
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