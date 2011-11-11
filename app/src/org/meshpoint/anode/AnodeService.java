package org.meshpoint.anode;

import java.util.HashMap;

import org.meshpoint.anode.Runtime.IllegalStateException;
import org.meshpoint.anode.Runtime.InitialisationException;
import org.meshpoint.anode.Runtime.NodeException;
import org.meshpoint.anode.Runtime.StateListener;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

public class AnodeService extends IntentService {

	private static String TAG = "anode::AnodeService";
	
	/**********************
	 * Instance table
	 ***********************/
	private static int counter;
	private static HashMap<String, Isolate> instances = new HashMap<String, Isolate>();
	
	static synchronized String addInstance(Isolate isolate) {
		String instance = String.valueOf(counter++);
		instances.put(instance, isolate);
		return instance;
	}
	
	static synchronized Isolate getInstance(String instance) {
		return instances.get(instance);
	}
	
	static synchronized void removeInstance(String instance) {
		instances.remove(instance);
	}
	
	static synchronized String soleInstance() {
		String instance = null;
		if(instances.size() == 1)
			instance = instances.keySet().iterator().next();
		return instance;
	}

	/**********************
	 * Service
	 **********************/
	public AnodeService() {
		super(":anode.AnodeService");
	}

    private void initRuntime(String[] opts) {
    	try {
    		Runtime.initRuntime(this, opts);
		} catch (InitialisationException e) {
			Log.v(TAG, "AnodeService.initRuntime: exception: " + e + "; cause: " + e.getCause());
		}
    }

	@Override
	protected void onHandleIntent(Intent intent) {
		String options = intent.getStringExtra(AnodeReceiver.OPTS);
		String[] opts = options == null ? null : options.split("\\s");
		initRuntime(opts);
		String action = intent.getAction();

		/* we should not get a stop action; should have been intercepted by the receiver */
		if(AnodeReceiver.ACTION_STOP.equals(action)) {
			Log.v(TAG, "AnodeService.onHandleIntent::stop: internal error");
			return;
		}

		/* get the launch commandline */
		String args = intent.getStringExtra(AnodeReceiver.CMD);
		
		/* if no cmdline was sent, then launch the activity for interactive behaviour */
		if(args == null || args.isEmpty()) {
			intent.setClassName(getApplication(), AnodeActivity.class.getName());
			getApplication().startActivity(intent);
			return;
		}

		/* if an instance id was specified, this is an error */
		String instance = intent.getStringExtra(AnodeReceiver.INST);
		if(instance != null) {
			Log.v(TAG, "AnodeService.onHandleIntent:: instance specified for START action; ignoring");
		}

		/* create a new instance based on the supplied args */
		ArgProcessor argProcessor = new ArgProcessor(intent.getExtras(), args);
		String[] processedArgs = argProcessor.process();

		/* launch directly */
		try {
			Isolate isolate = Runtime.createIsolate();
			isolate.addStateListener(new ServiceListener(addInstance(isolate)));
			isolate.start(processedArgs);
		} catch (IllegalStateException e) {
			Log.v(TAG, "AnodeReceiver.onReceive::start: exception: " + e + "; cause: " + e.getCause());
		} catch (NodeException e) {
			Log.v(TAG, "AnodeReceiver.onReceive::start: exception: " + e + "; cause: " + e.getCause());
		}
	}

	class ServiceListener implements StateListener {
		String instance;
		
		private ServiceListener(String instance) {
			this.instance = instance;
		}

		@Override
		public void stateChanged(final int state) {
			/* exit remove the instance if exited */
			if(state == Runtime.STATE_STOPPED) {
				removeInstance(instance);
			}
		}
	}

}