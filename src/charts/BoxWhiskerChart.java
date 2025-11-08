package charts;

import java.awt.BorderLayout;
import java.awt.geom.Ellipse2D;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.renderer.category.BoxAndWhiskerRenderer;
import org.jfree.chart.ui.ApplicationFrame;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerCalculator;
import org.jfree.data.statistics.BoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.BoxAndWhiskerItem;
import org.jfree.data.statistics.DefaultBoxAndWhiskerCategoryDataset;
import org.jfree.data.statistics.DefaultBoxAndWhiskerXYDataset;

public class BoxWhiskerChart extends ApplicationFrame {
	
	public BoxWhiskerChart(String[] filenames, String applicationTitle, String chartTitle) {
		super(applicationTitle);
		JFreeChart bwChart = null;
		
		try {
			
			Map<Integer, Double> datasetMapMEC = ReadData.readFile(filenames[0]);
			Map<Integer, Double> datasetMapRPL = ReadData.readFile(filenames[1]);
			bwChart = this.createChart(datasetMapMEC, datasetMapRPL);
			
			CategoryPlot plot = (CategoryPlot) bwChart.getPlot();
			BoxAndWhiskerRenderer renderer = new BoxAndWhiskerRenderer();
			renderer.setMeanVisible(false);        // show mean marker
			renderer.setFillBox(true);            // fill the box area
			renderer.setMedianVisible(true);      // default true, ensures median line
			renderer.setUseOutlinePaintForWhiskers(true);
			renderer.setWhiskerWidth(0.5);        // default is 0.5, can adjust for better proportions
			// Customize mean marker
			renderer.setDefaultShape(new Ellipse2D.Double(-2, -2, 1, 1));
			
			plot.setRenderer(renderer);
			
			
			ChartPanel chartPanel = new ChartPanel(bwChart);
			//chartPanel.setPreferredSize(new java.awt.Dimension(700, 500));
			setLayout(new BorderLayout());
			setSize(800,500);
			setContentPane(chartPanel);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private JFreeChart createChart(Map<Integer, Double> datasetMEC, Map<Integer, Double> datasetRPL) {
		DefaultBoxAndWhiskerCategoryDataset dataset = new DefaultBoxAndWhiskerCategoryDataset();
		
		
		
		BoxAndWhiskerCategoryDataset boxDataset = this.convertToBoxWhiskerDataset(datasetMEC, datasetRPL);
		JFreeChart chart = ChartFactory.createBoxAndWhiskerChart(
				"NFR Satisfaction Levels", 
				"Group", 
				"Value", 
				boxDataset, 
				true);
		return chart;	
	}
	
	private BoxAndWhiskerCategoryDataset convertToBoxWhiskerDataset(Map<Integer, Double> datasetMapMEC, Map<Integer, Double> datasetMapRPL) {
		
		
		DefaultBoxAndWhiskerCategoryDataset  boxDataset = new DefaultBoxAndWhiskerCategoryDataset();
		
		// First dataset MEC
		List<Double> valuesMEC = new ArrayList<>(datasetMapMEC.values());
		boxDataset.add(valuesMEC, "MEC (Coulombs)", "Non-Functional Requirement");
		
		// Second dataset RPL
		List<Double> valuesRPL = new ArrayList<>(datasetMapRPL.values());
		valuesRPL.replaceAll(x -> x * 100);
		boxDataset.add(valuesRPL, "RPL (%)", "Non-Functional Requirement");
		
		/*
		BoxAndWhiskerItem item = BoxAndWhiskerCalculator.calculateBoxAndWhiskerStatistics(values);
		
		boxDataset.add(
				item, 
				measure, 
				"Over all time");
		*/
		
		// 3) Diagnostic output: confirm dataset dimensions and stats
		/*
        System.out.println("=== Box Dataset Diagnostics ===");
        System.out.println("number of rows (series): " + boxDataset.getRowCount());
        System.out.println("number of columns (categories): " + boxDataset.getColumnCount());
        System.out.println("values in original list: " + values.size());
        System.out.println("BoxAndWhiskerItem from BoxAndWhiskerCalculator: " + item);
        // Print main numeric stats individually (null-checks)
        Number mean = item.getMean();
        Number median = item.getMedian();
        Number q1 = item.getQ1();
        Number q3 = item.getQ3();
        Number min = item.getMinRegularValue();
        Number max = item.getMaxRegularValue();
        System.out.println("mean=" + mean + ", median=" + median + ", q1=" + q1 + ", q3=" + q3);
        System.out.println("minRegular=" + min + ", maxRegular=" + max);
        System.out.println("outliers (count) = " + (item.getOutliers() == null ? 0 : item.getOutliers().size()));
        System.out.println("================================");
        */
		return boxDataset;
	}

}
