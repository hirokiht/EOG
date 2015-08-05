package tw.edu.ncku.eog;

import android.app.Activity;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.app.Fragment;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ToggleButton;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link TimerFragment.OnTimerListener} interface
 * to handle interaction events.
 */
public class TimerFragment extends Fragment implements CompoundButton.OnCheckedChangeListener{
    private ToneGenerator toneGenerator;
    private ToggleButton testButton;
    private TextView timerText;
    private ProgressBar timerProgress;
    private boolean start = false;
    private CountDownTimer timer;

    private OnTimerListener timerCallback;

    public TimerFragment() {
        this.setRetainInstance(true);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,ToneGenerator.MAX_VOLUME);
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt("progress", timerProgress.getProgress());
        outState.putBoolean("btnEnabled", testButton.isEnabled());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_timer, container, false);
        timerProgress = (ProgressBar) view.findViewById(R.id.progressBar);
        timerText = (TextView) view.findViewById(R.id.textView);
        testButton = (ToggleButton) view.findViewById(R.id.testButton);
        if(savedInstanceState != null && savedInstanceState.containsKey("progress")){
            int progress = savedInstanceState.getInt("progress",getResources().getInteger(R.integer.countdown_seconds));
            timerProgress.setProgress(progress);
            timerText.setText(String.valueOf(progress));
            if(progress == 0)
                timerText.setTextColor(0xffff0000); //red
            testButton.setEnabled(savedInstanceState.getBoolean("btnEnabled", false));
            testButton.setChecked(start);
        }
        testButton.setOnCheckedChangeListener(this);
        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            timerCallback = (OnTimerListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnTimerListener");
        }
        if(timer != null)
            return;
        timer = new CountDownTimer(activity.getResources().getInteger(R.integer.countdown_seconds)*1000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timerText.setText(String.valueOf(millisUntilFinished/1000));
                timerProgress.setProgress((int)millisUntilFinished/1000);
            }

            @Override
            public void onFinish() {
                start = false;
                toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK);
                timerText.setTextColor(0xffff0000); //red
                timerText.setText("0");
                timerProgress.setProgress(0);
                AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
                anim.setDuration(40); //You can manage the blinking time with this parameter
                anim.setStartOffset(160);
                anim.setRepeatMode(Animation.REVERSE);
                anim.setRepeatCount(5);
                timerText.startAnimation(anim);
                timerCallback.onTimerStateChange(false, true);
            }
        };
    }

    @Override
    public void onDetach() {
        super.onDetach();
        timerCallback = null;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(buttonView == testButton)
            timerCallback.onTimerStateChange(isChecked, false);
    }

    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     */
    public interface OnTimerListener {
        void onTimerStateChange(boolean start, boolean finish);
    }

    public void start(){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                testButton.setEnabled(true);
            }
        });
        if(!start && timerProgress.getProgress() == 30) {
            timer.start();
            start = true;
        }
    }

    public void reset(){
        reset(false);
    }

    public void reset(final boolean ready){
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if(testButton != null){
                    testButton.setEnabled(ready);
                    testButton.setChecked(false);
                }
                if(timerText != null) {
                    timerText.setTextColor(0xff000000); //BLACK
                    timerText.setText(ready ? String.valueOf(getResources().getInteger(R.integer.countdown_seconds)) : "-");
                }
            }
        });
        timerProgress.setProgress(getResources().getInteger(R.integer.countdown_seconds));
        if(timer != null)
            timer.cancel();
        start = false;
    }
}
