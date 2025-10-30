package charts;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jfree.data.category.DefaultCategoryDataset;

public class ReadData {
		
	public ReadData() { }
	
	
	
	protected Map<Integer, Double> readFile(String filePath) throws FileNotFoundException, IOException {
		Map<Integer, Double> values = new LinkedHashMap<>();
		
		try (BufferedReader bufReader = new BufferedReader(new FileReader(filePath))) {
			String line;
			
			while ((line = bufReader.readLine()) != null) {
				String[] parts = line.trim().split("\\s+"); // Split at whitespace
				if (parts.length == 2) {
					int timestamp = Integer.parseInt(parts[0]);
					double value = Double.parseDouble(parts[1]);
					values.put(timestamp, value); // Timestamp is like an index for the value
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return values;
	}
	
	public DefaultCategoryDataset getDataset(String filename) {
		Map<Integer, Double> values = null;
		
		try {
			values = this.readFile(filename);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		DefaultCategoryDataset dataset = new DefaultCategoryDataset( );
		for (Map.Entry<Integer, Double> val : values.entrySet()) {
			System.out.println(val.getKey()+""+val.getValue());
			dataset.addValue(val.getValue(), "x-axis title", val.getKey());
		}
		
		return dataset;
	}
}
