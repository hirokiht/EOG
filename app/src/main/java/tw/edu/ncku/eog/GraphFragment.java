package tw.edu.ncku.eog;


import android.os.Bundle;
import android.app.Fragment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
    private float samplingPeriod = 31f/1000f; //in seconds

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
        energyGraph.setTitle(getString(R.string.energy_title));
        energyGraph.addSeries(alphaEnergySeries);
        rawDataSeries.setColor(0xffff0000);
        energyGraph.getSecondScale().addSeries(rawDataSeries);
        energyGraph.getSecondScale().setMaxY(100);
        viewport = energyGraph.getViewport();
        viewport.setXAxisBoundsManual(true);
        viewport.setScalable(true);
        viewport.setScrollable(true);
        labelRenderer = energyGraph.getGridLabelRenderer();
        labelRenderer.setHorizontalAxisTitle(getString(R.string.time_axis));
        labelRenderer.setVerticalAxisTitle(getString(R.string.y_label));
        labelRenderer.setVerticalLabelsSecondScaleColor(0xffff0000);
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

    public void resetData( float[] data ){    //data is FFT Result up to 32Hz with 50% overlap
        final DataPoint[] dataPoints = data == null ? new DataPoint[]{new DataPoint(0,0)} : new DataPoint[data.length];
        if(data == null){
            spectrumSeries.resetData(dataPoints);
            return;
        }
        float max = 0f, power = 0f;
        for(float d : data)
            if(d > max)
                max = d;
        final float maxRatio = max;
        for(int i = BEGIN_FREQ ; i < END_FREQ ; i++)
            power += data[i];
        for(int i = 0 ; i < data.length; i++)
            dataPoints[i] = new DataPoint((double)i/(samplingPeriod*2f*data.length),data[i]);
        final double powerRatio = power/0.4f*100;
        energyMeter.setProgress((int) (powerRatio+0.5));   //set range is 0% to 50%
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                spectrumSeries.resetData(dataPoints);
                spectrumGraph.getViewport().setMaxX(dataPoints.length);
                spectrumGraph.getViewport().setMaxY(maxRatio);
                alphaEnergySeries.appendData(new DataPoint(alphaEnergySeries.isEmpty()? dataPoints.length/32f :
                        alphaEnergySeries.getHighestValueX() + 0.5f, powerRatio), true, 30);
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
                rawDataSeries.appendData(new DataPoint(rawDataSeries.getHighestValueX() + samplingPeriod, data), true, 480);
                energyGraph.getViewport().setMinX((int)rawDataSeries.getLowestValueX());
                energyGraph.getViewport().setMaxX(Math.ceil(rawDataSeries.getHighestValueX()));
            }
        });
    }

    public void appendRawData(@NonNull final float[] buffer ){
        handler.post(task = new Runnable() {
            @Override
            public void run() {
                for(float data : buffer)
                    rawDataSeries.appendData(new DataPoint(rawDataSeries.getHighestValueX() + samplingPeriod , data), true, 480);
                energyGraph.getViewport().setMinX((int)rawDataSeries.getLowestValueX());
                energyGraph.getViewport().setMaxX(Math.ceil(rawDataSeries.getHighestValueX()));
            }
        });
    }
}
