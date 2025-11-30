package charts;
import org.python.util.PythonInterpreter;
import org.python.core.*;

public class RunPython {
    public static void main(String[] args) {
        // Create a Python interpreter
        PythonInterpreter pythonInterpreter = new PythonInterpreter();

        // Set script file path
        String scriptPath = "./createCharts.py";

        // Execute the script
        pythonInterpreter.execfile(scriptPath);

        // Close the interpreter
        pythonInterpreter.close();
    }
}