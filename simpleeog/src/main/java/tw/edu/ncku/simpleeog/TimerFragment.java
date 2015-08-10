package tw.edu.ncku.simpleeog;

import android.app.Activity;
import android.app.Fragment;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.ToggleButton;

import com.example.simpleeog.R;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * {@link TimerFragment.OnTimerListener} interface
 * to handle interaction events.
 */
public class TimerFragment extends Fragment implements CompoundButton.OnCheckedChangeListener{
    private ToneGenerator toneGenerator;
    private ToggleButton testButton;
    private TimePicker timePicker;
    private boolean start = false;
    private CountDownTimer timer;
    private int countdown_seconds = 0;
    private int progress =0;
    private final Handler handler = new Handler();

    private OnTimerListener timerCallback;

    public TimerFragment() {
        this.setRetainInstance(true);
        toneGenerator = new ToneGenerator(AudioManager.STREAM_NOTIFICATION,ToneGenerator.MAX_VOLUME);
    }

    @Override
    public void onSaveInstanceState(Bundle outState){
        super.onSaveInstanceState(outState);
        outState.putInt("progress", progress);
        outState.putBoolean("btnEnabled", testButton.isEnabled());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_timer, container, false);
        timePicker = (TimePicker) view.findViewById(R.id.timePicker);
        timePicker.setIs24HourView(true);
        timePicker.setEnabled(false);
        testButton = (ToggleButton) view.findViewById(R.id.testButton);
        if(savedInstanceState != null && savedInstanceState.containsKey("progress")){
            int progress = savedInstanceState.getInt("progress",countdown_seconds);
            timePicker.setCurrentHour(progress/60);
            timePicker.setCurrentMinute(progress%60);
            testButton.setEnabled(savedInstanceState.getBoolean("btnEnabled", false));
            testButton.setChecked(start);
        }else{
            timePicker.setCurrentHour(countdown_seconds / 60);
            timePicker.setCurrentMinute(countdown_seconds%60);
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
        countdown_seconds = getResources().getInteger(R.integer.countdown_seconds);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        timerCallback = null;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if(buttonView == testButton) {
            if(isChecked)
                start();
            else reset();
            timerCallback.onTimerStateChange(isChecked, false);
        }
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
        int countdown = (timePicker.getCurrentMinute()+60*timePicker.getCurrentHour())*1000;
        if(!start && countdown > 0 && timer == null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    testButton.setEnabled(true);
                    timePicker.setEnabled(false);
                }
            });
            timer = new CountDownTimer(countdown, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    progress = (int)millisUntilFinished/1000;
                    timePicker.setCurrentHour(progress/60);
                    timePicker.setCurrentMinute(progress%60);
                }

                @Override
                public void onFinish() {
                    start = false;
                    toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK);
                    progress = 0;
                    timePicker.setCurrentHour(0);
                    timePicker.setCurrentMinute(0);
                    AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
                    anim.setDuration(40); //You can manage the blinking time with this parameter
                    anim.setStartOffset(160);
                    anim.setRepeatMode(Animation.REVERSE);
                    anim.setRepeatCount(5);
                    timePicker.startAnimation(anim);
                    timerCallback.onTimerStateChange(false, true);
                    timer = null;
                }
            };
            timer.start();
            start = true;
        }
    }

    public void reset(){
        reset(false);
    }

    public void reset(final boolean ready){
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (testButton != null) {
                    testButton.setEnabled(ready);
                    testButton.setChecked(false);
                }
                if (timePicker != null) {
                    timePicker.setCurrentHour(countdown_seconds / 60);
                    timePicker.setCurrentMinute(countdown_seconds % 60);
                    timePicker.setEnabled(ready);
                }
            }
        });
        progress = countdown_seconds;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        start = false;
    }
}
