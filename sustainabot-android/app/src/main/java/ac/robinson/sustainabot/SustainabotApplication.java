package ac.robinson.sustainabot;

import android.app.Application;

import com.facebook.drawee.backends.pipeline.Fresco;

public class SustainabotApplication extends Application {
	@Override
	public void onCreate() {
		super.onCreate();
		Fresco.initialize(this);
	}
}
