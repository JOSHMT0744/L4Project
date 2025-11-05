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
		
		DefaultCategoryDataset dataset = ReadData.getDataset(filename);
		JFreeChart lineChart = ChartFactory.createLineChart(
				chartTitle, 
				applicationTitle, 
				"Timestep (t)",
				dataset, PlotOrientation.VERTICAL,
				true, true, false);
		
		ChartPanel chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500, 367));
		setContentPane(chartPanel);
	}
}
