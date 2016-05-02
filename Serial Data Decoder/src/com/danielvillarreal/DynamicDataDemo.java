package com.danielvillarreal;

/* ===========================================================
 * JFreeChart : a free chart library for the Java(tm) platform
 * ===========================================================
 *
 * (C) Copyright 2000-2004, by Object Refinery Limited and Contributors.
 *
 * Project Info:  http://www.jfree.org/jfreechart/index.html
 *
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation;
 * either version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with this
 * library; if not, write to the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 *
 * [Java is a trademark or registered trademark of Sun Microsystems, Inc. 
 * in the United States and other countries.]
 *
 * --------------------
 * DynamicDataDemo.java
 * --------------------
 * (C) Copyright 2002-2004, by Object Refinery Limited.
 *
 * Original Author:  David Gilbert (for Object Refinery Limited).
 * Contributor(s):   -;
 *
 * $Id: DynamicDataDemo.java,v 1.12 2004/05/07 16:09:03 mungady Exp $
 *
 * Changes
 * -------
 * 28-Mar-2002 : Version 1 (DG);
 *
 */



import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.time.Millisecond;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;

/**
 * A demonstration application showing a time series chart where you can dynamically add
 * (random) data by clicking on a button.
 *
 */
@SuppressWarnings("serial")
public class DynamicDataDemo extends ApplicationFrame  {

	/** The time series data. */
	private XYSeries[] series = new XYSeries[16];

	int WindowSize = 1000;
	int channelCount = 0;
	int y = 0;

	private String[] DataSeriesName = {
			"PE0",
			"PE1",
			"PE2",
			"PE3",
			"PD7",
			"PD6",
			"PD5",
			"PD4",
			"PB4",
			"PB5",
			"PD3",
			"PD2",
			"PK0",
			"PK1",
			"PK2",
			"PK3"
	};

	/** The most recent value added. */
	private double lastValue = 100.0;

	public ChartFrame frame;
	public JFreeChart chart;
	public XYSeriesCollection dataset;

	public DynamicDataDemo(final String title) {

		super(title);
		dataset = new XYSeriesCollection( );
		for(int i = 0; i < DataSeriesName.length; i++){
			series[i] = new XYSeries(DataSeriesName[i]);
			dataset.addSeries(this.series[i]);
		}

		chart = createChart(dataset);
		chart.getXYPlot().getRenderer().setSeriesPaint(0, Color.blue);

		final ChartPanel chartPanel = new ChartPanel(chart);

		final JPanel content = new JPanel(new BorderLayout());
		content.add(chartPanel);
		frame = new ChartFrame("RealTimeChart", chart);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible(true);
		chartPanel.setPreferredSize(new java.awt.Dimension(800, 800));
		setContentPane(content);

	}

	private JFreeChart createChart(final XYDataset dataset) {
		final JFreeChart result = ChartFactory.createXYLineChart(
				"TIVA Analog Data", 
				"Time", 
				"Value",
				dataset, 
				PlotOrientation.VERTICAL ,
				true , true , false);

		final XYPlot plot = result.getXYPlot();
		ValueAxis axis = plot.getDomainAxis();
		axis.setAutoRange(true);
		axis.setFixedAutoRange(WindowSize);  // 60 seconds
		//axis.setRange(0.0, 4095.0); 
		axis = plot.getRangeAxis();
		axis.setRange(0.0, 4095.0); 
		// axis.setAutoRange(true);
		return result;
	}



	public void addNewSample(Sample mySample, Millisecond ms, boolean notify){
		//System.out.println("Time: " + ms.toString() + " Sample value: " + mySample.channelValue + " channel: " + mySample.chId);
		if(mySample.chId > 3){
			series[mySample.chId].add(channelCount,mySample.channelValue);
			
			if(channelCount++ == WindowSize){
				channelCount = 0;
				y++;
				Arrays.asList(series).stream().forEach(e -> e.clear());
			}
		}
	}

	public void updateChart(){
		chart = ChartFactory.createXYLineChart("Real time graph", "x-series", "y-series",
				dataset, PlotOrientation.VERTICAL, true, true, true);
		frame = new ChartFrame("Real time graph", chart);
	}





}

