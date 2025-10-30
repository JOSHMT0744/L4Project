package charts;

import org.jfree.chart.ChartPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;

public class LineChart extends ApplicationFrame{
	
	public LineChart(String filename, String applicationTitle, String chartTitle) {
		super(applicationTitle);
		// applicationTile
		JFreeChart lineChart = ChartFactory.createLineChart(
				chartTitle, 
				"Timestep (t)",
				applicationTitle, 
				this.getDataset(filename), PlotOrientation.VERTICAL,
				true, true, false);
		
		ChartPanel chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500, 367));
		setContentPane(chartPanel);
	}
	
	private DefaultCategoryDataset getDataset(String filename) {
		ReadData readObj = new ReadData();
		DefaultCategoryDataset dataset = readObj.getDataset(filename);
		return dataset;
	}
}
