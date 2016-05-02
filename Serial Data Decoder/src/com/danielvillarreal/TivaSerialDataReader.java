package com.danielvillarreal;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import org.apache.commons.codec.binary.Hex;
import org.jfree.data.time.Millisecond;
import org.jfree.ui.RefineryUtilities;

public class TivaSerialDataReader{

	private DynamicDataDemo demo;
	String port1 = "/dev/tty.HC-06-DevB";
	String port2 = "/dev/tty.SLAB_USBtoUART";

	public TivaSerialDataReader(){
		demo = new DynamicDataDemo("TIVA Demo");
		//demo.pack();
		//RefineryUtilities.centerFrameOnScreen(demo);
		//demo.setVisible(true);

		try{
			this.connect(port1);
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

			try{

				int nextChannel = 0;
				boolean reConfigureId = true;
				int idPosition = 0;
				ArrayList<Sample> data;
				int blockSize = 16;

				while(in.available() > 0){

					if(reConfigureId){
						String idSample[] = new String[2];

						boolean valid = false;
						while(!valid){
							idSample[0] = Hex.encodeHexString(readFromStream(this.in,2));
							idSample[1] = Hex.encodeHexString(readFromStream(this.in,2));
							
							if(!((idSample[0].equals(idSample[1])))){
								valid = true;
							}
						}

						idPosition = findIdPosition(idSample[0],idSample[1]);

						ArrayList<Sample>idFindSet = new ArrayList<Sample>();
						idFindSet.add(rawStringToSample(idSample[0], idPosition));
						idFindSet.add(rawStringToSample(idSample[1], idPosition));
						addSetToChart(demo, idFindSet);


						nextChannel  = extractId(idPosition,idSample[1]);
						nextChannel++;
					}
					reConfigureId = false;
					data = new ArrayList<Sample>();
					for(int i = 0; i <= blockSize; i++){
						Sample newSample = readNextSample(in,2,idPosition);
						if(newSample.chId != nextChannel){
							//Error in sequence
							reConfigureId = true;
							//Transfer available correct data
							startBlockTranfer(data,blockSize);
							break;
						}else{
							data.add(newSample);
							nextChannel++;
							checkBlockTransferReady(data,blockSize);
							if(nextChannel > 16){
								nextChannel = 0;
							}
						}
					}


				}


				/*while(in.available() > 0){
					byte temp[] = new byte[2];
					in.read(temp);
					System.out.println(Hex.encodeHexString(temp));
				}*/

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