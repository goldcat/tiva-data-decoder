package com.danielvillarreal;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.BufferedInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

import org.apache.commons.codec.binary.Hex;
import org.jfree.data.time.Millisecond;

public class TivaSerialDataReader{

	String port1 = "/dev/tty.HC-06-DevB";
	String port2 = "/dev/tty.SLAB_USBtoUART";

	SerialPort serialPort;

	private final int NUM_OF_SAMPLES = 500000;
	Thread readerThread;
	ArrayList<String> writeBuffer;

	float startTime;
	float endTime;

	public TivaSerialDataReader(){
		attempConnection(port1);

	}

	private void saveToFile(ArrayList<String> buff){
		printTime();
		System.out.println("Writing to file.");
		try {
			FileWriter writer = new FileWriter("output.txt");
			for(String str: buff){
				writer.write(str);
			}
			writer.flush();
			writer.close();
			printTime();
		} catch (IOException e) {
			e.printStackTrace();
		} 


	}

	private void printTime(){
		endTime = System.nanoTime();
		System.out.println("Elapsed: " + (endTime-startTime)/(1000000000.0));
	}

	private void addToWriteBuffer(String s){
		if(writeBuffer == null){
			writeBuffer = new ArrayList<String>(NUM_OF_SAMPLES * 2);
		}

		writeBuffer.add(s+"\n");
	}

	void attempConnection(String port){
		int time = 3000;
		try {
			this.connect(port);
			System.out.println("Connected to port!");
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("Error connecting");
			System.exit(0);

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
				serialPort = (SerialPort) commPort;
				serialPort.setSerialPortParams(921600,SerialPort.DATABITS_8,SerialPort.STOPBITS_1,SerialPort.PARITY_NONE);


				BufferedInputStream bis = new BufferedInputStream(serialPort.getInputStream());

				readerThread = (new Thread(new SerialReader(bis)));
				startTime = System.nanoTime();
				readerThread.start();


			}
			else{
				System.out.println("Error: Only serial ports are handled by this example.");
			}
		}     
	}

	public class ShutdownSaver implements Runnable{

		PrintWriter wr;

		public ShutdownSaver(PrintWriter wr) {

			this.wr = wr;
		}

		@Override
		public void run() {
			wr.close();
			wr.flush();
			System.out.println("closed file");

		}

	}



	public class SerialReader implements Runnable {
		BufferedInputStream bis;

		public SerialReader (BufferedInputStream bis){
			this.bis = bis;
		}

		public void run ()
		{
			System.out.println("Srted run");

			int currentId = -1;
			int countSinceError = 0;
			float count = 0;
			float errorCount = 0;

			try{

				while(!(bis.available() >= 2)){
					//Wait until there is enough data
				}

				System.out.println("Data is available!");

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
						addToWriteBuffer("Sequnece off. Adjusted!");
					}else{
						initialError = false;
						bis.reset();
					}
				}

				while(true){
					if(bis.available() > 2){

						byte temp[] = new byte[2];
						bis.read(temp);
							
						String newSample = Hex.encodeHexString(temp);

						if(currentId == -1){
							currentId = extractId(2, newSample);
						}

						if(extractId(2,newSample) != currentId){
							String debug = "";

							bis.mark(4);

							//Error id mismatch
							byte discard[] = new byte[2];
							//Take 4 words out
							bis.read(discard);

							String checkSample = Hex.encodeHexString(discard);
							
							debug += ("error in id: " + currentId + " cse: " + countSinceError + " sample: " + newSample);
							addToWriteBuffer(debug);
							
							if(extractId(2,checkSample) != (currentId+1)){
								bis.reset();
								readFromStream(bis, 1);
								currentId+=2;
								addToWriteBuffer("Not fix, reset(). Next id must be: " + currentId );
							}else{
								currentId += 2;
							}

							

							errorCount++;
							countSinceError = 0;
						}else{
							addToWriteBuffer(newSample);
							currentId++;
							countSinceError++;
						}

						if(currentId > 15){
							currentId = 0;
						}

						count++;

						if(count >= NUM_OF_SAMPLES){
							System.out.println("Entered closing");
							String err = ("Error rate: " + (errorCount/count)*100.0 + "%" + "\t error count: " + errorCount + "\t count: " + count);
							addToWriteBuffer(err);
							System.out.println(err);
							saveToFile(writeBuffer);
							serialPort.close();
							System.exit(0);
						}


					}
					
				}


			}catch ( Exception e){
				e.printStackTrace();
			}

		}
	}

	private boolean detectError(String sample, int currentId){
		return extractId(2,sample) != currentId;
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


	public static void main ( String[] args){
		new TivaSerialDataReader();
	}
}