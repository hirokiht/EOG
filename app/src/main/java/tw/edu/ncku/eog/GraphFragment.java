package tw.edu.ncku.eog;


import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;


/**
 * A simple {@link Fragment} subclass.
 */
public class GraphFragment extends Fragment {
    private final static int BEGIN_FREQ = 8, END_FREQ = 12;

    private ProgressBar energyMeter;
    private GraphView spectrumGraph, energyGraph;
    private final BaseSeries<DataPoint> spectrumSeries = new LineGraphSeries<>(),
        rawDataSeries = new LineGraphSeries<>(), alphaEnergySeries = new LineGraphSeries<>();
    private final Handler handler = new Handler();
    private Runnable task;
    private float samplingPeriod = 8f/1000f; //in seconds

    public GraphFragment() {
        setRetainInstance(true);
        // Required empty public constructor
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_graph, container, false);
        spectrumGraph = (GraphView) view.findViewById(R.id.spectrum_graph);
        energyMeter = (ProgressBar) view.findViewById(R.id.energyMeter);
        spectrumGraph.setTitle(getString(R.string.spectrum));
        spectrumGraph.addSeries(spectrumSeries);
        Viewport viewport = spectrumGraph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setYAxisBoundsManual(true);
        viewport.setMaxY(100);
        GridLabelRenderer labelRenderer = spectrumGraph.getGridLabelRenderer();
        labelRenderer.setHorizontalAxisTitle(getString(R.string.x_label));
        labelRenderer.setVerticalAxisTitle(getString(R.string.y_label));
        energyGraph = (GraphView) view.findViewById(R.id.energy_graph);
        energyGraph.setTitle("Raw Data/Energy/Time Graph");
        energyGraph.addSeries(alphaEnergySeries);
        rawDataSeries.setColor(0xffff0000);
        energyGraph.getSecondScale().addSeries(rawDataSeries);
        energyGraph.getSecondScale().setMaxY(100);
        viewport = energyGraph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setScalable(true);
        viewport.setScrollable(true);
        labelRenderer = energyGraph.getGridLabelRenderer();
        labelRenderer.setHorizontalAxisTitle("time (s)");
        labelRenderer.setVerticalAxisTitle("Energy Ratio (%)");
        labelRenderer.setVerticalLabelsSecondScaleColor(0xffff0000);
        view.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1.5f));
        return view;
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(task);
        super.onPause();
    }

    public void resetData(){
        spectrumSeries.resetData(new DataPoint[]{new DataPoint(0, 0)});
        alphaEnergySeries.resetData(new DataPoint[]{new DataPoint(0, 0)});
        rawDataSeries.resetData(new DataPoint[]{new DataPoint(0, 0)});
    }

    public void resetData(float[] data){
        final DataPoint[] dataPoints = data == null ? new DataPoint[]{new DataPoint(0,0)} : new DataPoint[data.length];
        if(data == null){
            spectrumSeries.resetData(dataPoints);
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
        final double powerRatio = power/sum*100+0.5;
        energyMeter.setProgress((int) powerRatio);
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                spectrumSeries.resetData(dataPoints);
                spectrumGraph.getViewport().setMaxX(dataPoints.length);
                spectrumGraph.getViewport().setMaxY(maxRatio);
                alphaEnergySeries.appendData(new DataPoint(alphaEnergySeries.getHighestValueX() + (dataPoints.length * 2 * samplingPeriod), powerRatio), true, Integer.MAX_VALUE);
            }
        });
    }

    public float getSamplingPeriod(){
        return samplingPeriod;
    }

    public void setSamplingPeriod(float period){
        samplingPeriod = period;
    }

    public void appendRawData(final float data){
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                rawDataSeries.appendData(new DataPoint(rawDataSeries.getHighestValueX() + samplingPeriod, data), true, Integer.MAX_VALUE);
                energyGraph.getViewport().setMinX(0);
                energyGraph.getViewport().setMaxX(Math.ceil(rawDataSeries.getHighestValueX()));
            }
        });
    }

    public void appendRawData(@NonNull final float[] buffer ){
        float sum = 0f;
        for(float data : buffer)
            sum += data;
        final float avg = sum/buffer.length;
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                rawDataSeries.appendData(new DataPoint(rawDataSeries.getHighestValueX() + (samplingPeriod * buffer.length), avg), true, Integer.MAX_VALUE);
                energyGraph.getViewport().setMinX(0);
                energyGraph.getViewport().setMaxX(Math.ceil(rawDataSeries.getHighestValueX()));
            }
        });
    }
}
