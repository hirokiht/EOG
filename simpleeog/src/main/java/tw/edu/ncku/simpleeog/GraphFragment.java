package tw.edu.ncku.simpleeog;


import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.example.simpleeog.R;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


/**
 * A simple {@link Fragment} subclass.
 */
public class GraphFragment extends Fragment {
    private final static int BEGIN_FREQ = 8, END_FREQ = 12;

    private ProgressBar energyMeter;
    private GraphView energyGraph;
    private final LineGraphSeries<DataPoint> alphaEnergySeries = new LineGraphSeries<>();
    private final Handler handler = new Handler();
    private Runnable task;
    private float samplingPeriod = 31f/1000f; //in seconds

    public GraphFragment() {
        setRetainInstance(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        energyMeter = (ProgressBar) view.findViewById(R.id.energyMeter);
        energyGraph = (GraphView) view.findViewById(R.id.energy_graph);
        energyGraph.setTitle(getString(R.string.graph_title));
        energyGraph.addSeries(alphaEnergySeries);
        GridLabelRenderer labelRenderer = energyGraph.getGridLabelRenderer();
        labelRenderer.setHorizontalAxisTitle(getString(R.string.x_label));
        labelRenderer.setVerticalAxisTitle(getString(R.string.y_label));
        return view;
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(task);
        super.onPause();
    }

    public void resetData(){
        alphaEnergySeries.resetData(new DataPoint[]{new DataPoint(0, 0)});
    }

    public void resetData(@NonNull final float[] data){    //data is FFT Result up to 32Hz with 50% overlap
        float power = 0f;
        for(int i = BEGIN_FREQ ; i < END_FREQ ; i++)
            power += data[i];
        final double powerRatio = power/0.4f*100;
        energyMeter.setProgress((int) (powerRatio+0.5));   //set range is 0% to 50%
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                alphaEnergySeries.appendData(new DataPoint(alphaEnergySeries.isEmpty()? 0f :
                        alphaEnergySeries.getHighestValueX() + 0.5f, powerRatio), false, 30);
            }
        });
    }

    public float getSamplingPeriod(){
        return samplingPeriod;
    }

    public void setSamplingPeriod(float period){
        samplingPeriod = period;
    }
}
