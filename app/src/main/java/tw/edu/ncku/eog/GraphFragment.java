package tw.edu.ncku.eog;


import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


/**
 * A simple {@link Fragment} subclass.
 */
public class GraphFragment extends Fragment {
    private final static int BEGIN_FREQ = 8, END_FREQ = 12;

    private ProgressBar energyMeter;
    private GraphView graphView;
    private final LineGraphSeries<DataPoint> series = new LineGraphSeries<>();
    private final Handler handler = new Handler();
    private Runnable task;


    public GraphFragment() {
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        graphView = (GraphView) view.findViewById(R.id.graph);
        energyMeter = (ProgressBar) view.findViewById(R.id.energyMeter);
        graphView.setTitle(getString(R.string.spectrum));
        graphView.addSeries(series);
        Viewport viewport = graphView.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setMaxY(100);
        GridLabelRenderer labelRenderer = graphView.getGridLabelRenderer();
        labelRenderer.setHorizontalAxisTitle(getString(R.string.x_label));
        labelRenderer.setVerticalAxisTitle(getString(R.string.y_label));
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f));
        return view;
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(task);
        super.onPause();
    }

    public void resetData(){
        resetData(null);
    }

    public void resetData(float[] data){
        final DataPoint[] dataPoints = data == null ? new DataPoint[]{new DataPoint(0,0)} : new DataPoint[data.length];
        if(data == null){
            series.resetData(dataPoints);
            return;
        }
        float max = 0f, sum = 0f, power = 0f;
        for(float d : data) {
            if(d > max)
                max = d;
            sum += d;
        }
        for(int i = BEGIN_FREQ ; i < END_FREQ ; i++)
            power += data[i];
        for(int i = 0 ; i < data.length; i++)
            dataPoints[i] = new DataPoint((double)i,(double)data[i]/sum*100);
        final float maxRatio = max/sum*100;
        energyMeter.setProgress((int)(power/sum*100+0.5));
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                series.resetData(dataPoints);
                graphView.getViewport().setMaxX(dataPoints.length);
                graphView.getViewport().setMaxY(maxRatio);
            }
        });
    }
}
