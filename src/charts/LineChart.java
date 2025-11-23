package charts;

import org.jfree.chart.ChartPanel;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

public class LineChart extends ApplicationFrame{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private static XYSeriesCollection convertCategoryToXY(DefaultCategoryDataset categoryDataset) {
	    XYSeriesCollection xyCollection = new XYSeriesCollection();

	    // Each "row" in the category dataset becomes one XYSeries
	    for (int row = 0; row < categoryDataset.getRowCount(); row++) {
	        Comparable<?> rowKey = categoryDataset.getRowKey(row);
	        XYSeries series = new XYSeries(rowKey);

	        for (int col = 0; col < categoryDataset.getColumnCount(); col++) {
	            Comparable<?> colKey = categoryDataset.getColumnKey(col);

	            // Convert x-axis category label into a numeric value
	            double x;
	            try {
	                x = Double.parseDouble(colKey.toString());
	            } catch (NumberFormatException e) {
	                // fallback: use index instead
	                x = col;
	            }

	            Number y = categoryDataset.getValue(row, col);
	            if (y != null) {
	                series.add(x, y.doubleValue());
	            }
	        }

	        xyCollection.addSeries(series);
	    }

	    return xyCollection;
	}

	public LineChart(String filename, String applicationTitle, String chartTitle) {
		super(applicationTitle);
		// applicationTile
		
		DefaultCategoryDataset dataset = ReadData.getDataset(filename);
		// Convert DefaultCategoryDataset to XYSeriesCollection
		XYSeriesCollection xyCollection = convertCategoryToXY(dataset);
		
		JFreeChart lineChart = ChartFactory.createXYLineChart(
				chartTitle, 
				applicationTitle, 
				"Timestep (t)",
				xyCollection, PlotOrientation.VERTICAL,
				true, true, false);
		
		// Adjust x-axis spacing labels
		XYPlot plot = lineChart.getXYPlot();

	    NumberAxis xAxis = (NumberAxis) plot.getDomainAxis();
	    xAxis.setAutoRangeIncludesZero(false);
	    xAxis.setTickUnit(new NumberTickUnit(10));   // 👈 only show every 10th step
		
		ChartPanel chartPanel = new ChartPanel(lineChart);
		chartPanel.setPreferredSize(new java.awt.Dimension(500, 367));
		setContentPane(chartPanel);
	}
}
