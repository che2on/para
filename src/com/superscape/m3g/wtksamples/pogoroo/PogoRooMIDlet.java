/******************************************************************************/
/**
 *        @file        PogoRooMIDlet.java
 *        @brief        A simple example of a game using M3G
 *
 *        Copyright (C) 2004 Superscape plc
 *
 *        This file is intended for use as a code example, and
 *        may be used, modified, or distributed in source or
 *        object code form, without restriction, as long as
 *        this copyright notice is preserved.
 *
 *        The code and information is provided "as-is" without
 *        warranty of any kind, either expressed or implied.
 */

/******************************************************************************/
package com.superscape.m3g.wtksamples.pogoroo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.lang.IllegalArgumentException;

import java.util.Timer;
import java.util.TimerTask;

import javax.microedition.io.Connector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.StringItem;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.m3g.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;
import javax.microedition.sensor.ChannelInfo;
import javax.microedition.sensor.Data;
import javax.microedition.sensor.DataListener;
import javax.microedition.sensor.SensorConnection;
import javax.microedition.sensor.SensorInfo;
import javax.microedition.sensor.SensorManager;


public class PogoRooMIDlet extends MIDlet implements CommandListener, DataListener {
    // UserIDs for objects we use in the scene.
    static final int POGOROO_MOVE_GROUP_TRANSFORM_ID = 554921620;
    static final int CAMERA_GROUP_TRANSFORM_ID = 769302310;
    static final int POGOROO_TRANSFORM_ID = 347178853;
    static final int ROO_BOUNCE_ID = 418071423;
    
    
    
    
    private SensorConnection sensor;
    private static final int BUFFER_SIZE = 1;
    private static final boolean IS_TRIGGERING_EVENT_ALWAYS = false;
    private static int exEvent = -1;

    // Key event type IDs
    public static final int KEY_REPEATED = 0;
    public static final int KEY_PRESSED = 1;
    public static final int KEY_RELEASED = 2;
    
    
    public static final int _UP =5000;
    public static final int _DOWN =6000;
    public static final int _LEFT =7000;
    public static final int _RIGHT=8000;
    
    int dir = _UP;
    

    // Key IDs
    static final int keyNone = 0;
    static final int keyForward = 1;
    static final int keyBackward = 2;
    static final int keyLeft = 3;
    static final int keyRight = 4;
    static final int MaxHops = 10;
    static final float GroundEdge = 9.0f;
    private Display myDisplay = null;
    private PogoRooCanvas myCanvas = null;
    private Timer myRefreshTimer = new Timer();
    private TimerTask myRefreshTask = null;
    private Command exitCommand = new Command("Exit", Command.ITEM, 1);
    Graphics3D myGraphics3D = Graphics3D.getInstance();
    World myWorld = null;

    // Control objects for game play
    // control for 'roo - group transform and cameras
    private AnimationController animRoo = null;
    private Group tRoo = null;
    private Group tCams = null;
    private Group acRoo = null;
    private float dirRoo = 0.0f;
    private float dirCam = 0.0f;
    private int keyMoveRoo = keyNone;
    private int keyTurnRoo = keyNone;
    private int hopCount = 0;
    private int animTime = 0;
    private int animLength = 0;
    private int animLastTime = 0;
    private boolean okToHop = false;
    private float[] posRoo = new float[3];
    private float[] posRooLast = new float[3];
    private float[] posTemp = new float[3];
    private int edgeCount = 0;
    private float turnAngle;

    // lookup table for roo hops
    private float[] hopSteps =
        { 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.05f, 0.1f, 0.2f, 0.1f, 0.05f, 0.0f };
    int viewport_x;
    int viewport_y;
    int viewport_width;
    int viewport_height;

    /**
     * PogoRooMIDlet - default constructor.
     */
    public PogoRooMIDlet() {
        super();

        // Set up the user interface.
        myDisplay = Display.getDisplay(this);
        myCanvas = new PogoRooCanvas(this);
        myCanvas.setCommandListener(this);
        myCanvas.addCommand(exitCommand);
        
        
       
    }

    /**
     * startApp()
     */
    public void startApp() throws MIDletStateChangeException {
        myDisplay.setCurrent(myCanvas);

        try {
            // load the world from the M3D file
            myWorld = (World)Loader.load(
                    "/com/superscape/m3g/wtksamples/pogoroo/content/pogoroo.m3g")[0];
            getObjects();
            setupAspectRatio();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        sensor = openSensor();
		if (sensor==null) return;
		sensor.setDataListener(this, BUFFER_SIZE);

        myRefreshTask = new RefreshTask();

        // schedule a repeating timer to give us a framerate of 20fps.
        myRefreshTimer.schedule(myRefreshTask, 0, 50);
    }

    /**
     * Make sure that the content is rendered with the correct aspect ratio.
     */
    void setupAspectRatio() 
    {
        viewport_x = 0;
        viewport_y = 0;
        viewport_width = myCanvas.getWidth();
        viewport_height = myCanvas.getHeight();

        Camera cam = myWorld.getActiveCamera();

        float[] params = new float[4];
        int type = cam.getProjection(params);

        if (type != Camera.GENERIC) 
        {
            //calculate window aspect ratio
            float waspect = viewport_width / viewport_height;

            if (waspect < params[1]) {
                float height = viewport_width / params[1];
                viewport_height = (int)height;
                viewport_y = (myCanvas.getHeight() - viewport_height) / 2;
            } else {
                float width = viewport_height * params[1];
                viewport_width = (int)width;
                viewport_x = (myCanvas.getWidth() - viewport_width) / 2;
            }
        }
    }

    /**
     * getObjects()
     * get objects from the scene tree for use in the game AI
     */
    public void getObjects() {
        try {
            tRoo = (Group)myWorld.find(POGOROO_MOVE_GROUP_TRANSFORM_ID);
            tCams = (Group)myWorld.find(CAMERA_GROUP_TRANSFORM_ID);
            acRoo = (Group)myWorld.find(POGOROO_TRANSFORM_ID);
            animRoo = (AnimationController)myWorld.find(ROO_BOUNCE_ID);

            // get length of animation
            AnimationTrack track = acRoo.getAnimationTrack(0);
            animLength = 1000; // default length, 1 second

            if (track != null) 
            {
                KeyframeSequence ks = track.getKeyframeSequence();

                if (ks != null) {
                    animLength = ks.getDuration();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * hopRoo()
     * Hops the roo backwards or forwards
     */
    private void hopRoo() {
        // Move the kangaroo across the ground, but synchronize with animation
        if (animTime == 0) // OK to start!
         {
            hopCount = 0;
            okToHop = true;
        }

        // in hopping sequence
        if (okToHop) {
            switch (keyMoveRoo) {
            case keyForward:
            case keyBackward:

                // move according to direction and increment from look up table 
                // to get nice hop effect
                int oldHopCount = hopCount;
                hopCount = (animTime * 10) / animLength;

                // end of sequence
                if (hopCount >= MaxHops) {
                    okToHop = false;
                    hopCount = MaxHops - 1;
                }

                // add up all the steps in between positions in animation
                // this code always misses out increment zero, but that's
                // OK because it's zero anyway!
                turnAngle = (dirRoo * 3.14159f) / 180.0f;

                float h = 0f;

                for (int i = (oldHopCount + 1); i <= hopCount; i++)
                    h += hopSteps[i];

                float x = h * (float)Math.cos(turnAngle);
                float y = h * (float)Math.sin(turnAngle);

                if (keyMoveRoo == keyForward) {
                    tRoo.translate(-x, -y, 0.0f);
                } else {
                    tRoo.translate(x, y, 0.0f);
                }

                break;
            }
        }
    }

    /**
     * checkWorldEdge()
     * Stops the roo going off the edge of the world
     */
    private void checkWorldEdge() {
        // going off edge of ground
        tRoo.getTranslation(posRoo);

        if (edgeCount > 0) {
            edgeCount--;
        }

        try {
            // check to see if we have reached the edge of the world
            if ((Math.abs(posRoo[0]) > GroundEdge) || (Math.abs(posRoo[1]) > GroundEdge)) {
                edgeCount = 10;
                tRoo.setTranslation(posRooLast[0], posRooLast[1], posRooLast[2]);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * turnRoo()
     * Turns the roo and makes sure the camera follows.
     */
    private void turnRoo() {
        // turning Roo can happen any time
        switch (keyTurnRoo) {
        case keyLeft:
            dirRoo += 5f;
            dirCam -= 5f;
            tRoo.setOrientation(dirRoo, 0, 0, 1);
            tCams.setOrientation(dirCam, 0, 0, 1);
          //  keyTurnRoo = keyNone;
            break;

        case keyRight:
            dirRoo -= 5f;
            dirCam += 5f;
            tRoo.setOrientation(dirRoo, 0, 0, 1);
            tCams.setOrientation(dirCam, 0, 0, 1);
        //    keyTurnRoo = keyNone;
            break;

        default:

            if (dirCam > 4.9f) {
                dirCam -= 5.0f;
            } else if (dirCam < -4.9f) {
                dirCam += 5.0f;
            } else {
                dirCam = 0.0f;
            }

            tCams.setOrientation(dirCam, 0, 0, 1);

            break;
        }
    }

    /**
     * animateRoo()
     * Makes sure that the hopping animation loops correctly.
     */
    private void animateRoo(int worldTime) {
        // control the kangaroo animation sequence
        if (animLastTime == 0) {
            animLastTime = worldTime;
        }

        animTime += (worldTime - animLastTime);

        // initialise animation at end of sequence
        if (animTime > animLength) // sequence is ~1000ms
         {
            animRoo.setActiveInterval(worldTime, worldTime + 2000);
            animRoo.setPosition(0, worldTime);
            animTime = 0;
        }

        // update storage of last position and time
        animLastTime = worldTime;
    }

    /**
     * moveRoo()
     * Act on key presses and any collision detection to move the kangaroo
     */
    private void moveRoo(int worldTime) 
    {
        hopRoo();

        checkWorldEdge();
        keyMoveRoo = keyForward;
        turnRoo();

        animateRoo(worldTime);

        tRoo.getTranslation(posRooLast);
    }

    /**
     * pauseApp()
     */
    public void pauseApp() {
    }

    /**
     * destroyApp()
     */
    public void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        myRefreshTimer.cancel();
        myRefreshTimer = null;
        myRefreshTask = null;
    }

    /**
     * MIDlet paint method.
     */
    public void paint(Graphics g) {
        // clear any area of the screen that is not drawn to by m3g
        if ((g.getClipWidth() != viewport_width) || (g.getClipHeight() != viewport_height) ||
                (g.getClipX() != viewport_x) || (g.getClipY() != viewport_y)) {
            g.setColor(0x00);
            g.fillRect(0, 0, myCanvas.getWidth(), myCanvas.getHeight());
        }

        // render the 3D scene
        if ((myGraphics3D != null) && (myWorld != null)) {
            myGraphics3D.bindTarget(g);
            myGraphics3D.setViewport(viewport_x, viewport_y, viewport_width, viewport_height);
            myGraphics3D.render(myWorld);
            myGraphics3D.releaseTarget();
        }
    }

    /**
     * MIDlet keyEvent method.
     * 
     * 
     * 
     */
    public boolean isPressed(int xmin, int xmax, int ymin, int ymax, int x, int y)
	{
		
		if(x>=xmin && x<= xmax && y>=ymin &&y <=ymax)
		return true;
		return false;
	}
    
    
    
    
    private SensorConnection openSensor()
    {
		SensorInfo infos[] = SensorManager.findSensors("acceleration", null);
		if (infos.length == 0) return null;
		try{
			return (SensorConnection)Connector.open(infos[0].getUrl());
		}catch(SecurityException se){
			se.printStackTrace();
			return null;
		}
		catch(IOException ioe){
			ioe.printStackTrace();
			System.out.println("Couldn't open sensor : "
					+ infos[0].getUrl()+"!");
			return null;
		}
        catch(IllegalArgumentException iae) {
			iae.printStackTrace();
			return null;

        }
	}
    
    
    private static int getActionKey(double axis_x, double axis_y){
		// axis_x: LEFT or RIGHT
		if (Math.abs(axis_x)>Math.abs(axis_y)){
			return axis_x<0?Canvas.RIGHT:Canvas.LEFT;
		}
		// axis_y: UP or DOWN
		return axis_y<0?Canvas.UP:Canvas.DOWN;
	}

	/**
	 * The method returns action events that
	 * corresponds to the given acceleration data.
	 * Valid return values are:
	 * Canvas.UP
	 * Canvas.DOWN
	 * Canvas.RIGHT
	 * Canvas.LEFT
	 * @param data the acceleration data
	 * @return the action event array
	 */

	private static int[] data2actionEvents(Data[] data)
	{
		
		System.out.println("data xyz actions...");

		ChannelInfo cInfo = data[0].getChannelInfo();
		boolean isInts = cInfo.getDataType() == ChannelInfo.TYPE_INT? true: false;
		int[] events = new int[BUFFER_SIZE];

		if (isInts){
			int[][] ints = new int[2][BUFFER_SIZE];
			for (int i=0; i<2; i++){
				ints[i] = data[i].getIntValues();
			}
			for (int i=0; i<BUFFER_SIZE; i++){
				events[i] = getActionKey(ints[0][i], ints[1][i]);
			}
			return events;
		}
		double[][] doubles = new double[2][BUFFER_SIZE];
		for (int i=0; i<2; i++){
			doubles[i] = data[i].getDoubleValues();
		}
		for (int i=0; i<BUFFER_SIZE; i++){
			events[i] = getActionKey(doubles[0][i], doubles[1][i]);
		}
		return events;
	}

	public void dataReceived(SensorConnection sensor, Data[] d,	boolean isDataLost) {
		int[] events = data2actionEvents(d);

		for (int i=0; i<BUFFER_SIZE; i++){
			if (events[i] == exEvent && !IS_TRIGGERING_EVENT_ALWAYS)
				continue;

			exEvent = events[i];
			switch(events[i])
			{
			
			case Canvas.UP:
				System.out.println("UP xyz");
				analysePress("pressed", _UP);
        		//analysePress("released", _UP);

				break;
			case Canvas.DOWN:
				System.out.println("DOWN xyz");
				analysePress("pressed", _DOWN);
        	//	analysePress("released", _DOWN);
				break;
			case Canvas.LEFT:
				System.out.println("LEFT xyz");
				analysePress("pressed", _LEFT);
        		//analysePress("released", _LEFT);

				break;
			case Canvas.RIGHT:
				System.out.println("RIGHT xyz");
				analysePress("pressed", _RIGHT);
        		//analysePress("released", _RIGHT);
				break;
			default:
				//arrow.setText("");
			}
		}
	}
    
    
    
    public void analysePress(String type, int dir)
    {
    	
    	System.out.println("analysing press");
    	if(type.equals("pressed"))
    	{
    		
    		 if (keyMoveRoo == keyNone) 
    		 {
                 switch (dir) {
                 case Canvas.FIRE:
                     break;

                 case _UP:
                     keyMoveRoo = keyForward;
                   //  System.out.println("UP xyz");

                     break;

                 case _DOWN:
                     keyMoveRoo = keyBackward;
                   //  System.out.println("DOWN xyz");

                     break;
                 }

                 if (keyMoveRoo != keyNone) {
                     hopCount = MaxHops;
                 }
                 
                                 
             }
    		 
    		 
    		 switch ((dir))
             {
             case _LEFT:
                 keyTurnRoo = keyLeft;

                 break;

             case _RIGHT:
                 keyTurnRoo = keyRight;

                 break;
             }

    		
    	}
    	else
    	if(type.equals("released"))
    	{
    		
    		 switch (dir) {
             case _LEFT: //intentional fall through
             case _RIGHT:
                 keyTurnRoo = keyNone;

                 break;

             case _UP: //intentional fall through
             case _DOWN:
                 keyMoveRoo = keyNone;

                 break;
             }
    		
    	}
    	else
    	if(type.equals("repeated"))
    	{
    		
    	}
    	
    }
    
    
    
    
    public void keyEvent(int type, int keyCode) 
    {
        switch (type)
        {
        case KEY_REPEATED:
            break;

        case KEY_PRESSED:

            // game movement keys -	Roo requires synchronized move with animation, 
            // so can only accept key press when last move is complete
            if (keyMoveRoo == keyNone) {
                switch (myCanvas.getGameAction(keyCode)) {
                case Canvas.FIRE:
                    break;

                case Canvas.UP:
                    keyMoveRoo = keyForward;

                    break;

                case Canvas.DOWN:
                    keyMoveRoo = keyBackward;

                    break;
                }

                if (keyMoveRoo != keyNone) {
                    hopCount = MaxHops;
                }
            }

            // Roo can turn when it likes
            switch (myCanvas.getGameAction(keyCode)) {
            case Canvas.LEFT:
                keyTurnRoo = keyLeft;

                break;

            case Canvas.RIGHT:
                keyTurnRoo = keyRight;

                break;
            }

            break;

        case KEY_RELEASED:

            switch (myCanvas.getGameAction(keyCode)) {
            case Canvas.LEFT: //intentional fall through
            case Canvas.RIGHT:
                keyTurnRoo = keyNone;

                break;

            case Canvas.UP: //intentional fall through
            case Canvas.DOWN:
                keyMoveRoo = keyNone;

                break;
            }

            break;

        default:
            throw new IllegalArgumentException();
        }
    }

    /**
     * Handle commands.
     */
    public void commandAction(Command cmd, Displayable disp) {
        if (cmd == exitCommand) {
            try {
                destroyApp(false);
                notifyDestroyed();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Inner class for refreshing the view.
     */
    private class RefreshTask extends TimerTask {
        public void run() {
            // Get the canvas to repaint itself.
            if ((myCanvas != null) && (myGraphics3D != null) && (myWorld != null)) {
                int startTime = (int)System.currentTimeMillis();
                // update the control and game AI
                moveRoo(startTime);

                // Update the world to the current time.
                int validity = myWorld.animate(startTime);
                // Cause a repaint
                myCanvas.repaint(viewport_x, viewport_y, viewport_width, viewport_height);
            }
        }
    }

    /**
     * Inner class for handling the canvas.
     */
    class PogoRooCanvas extends GameCanvas {
        PogoRooMIDlet myRooMIDlet;

        /**
         * Construct a new canvas
         */
        PogoRooCanvas(PogoRooMIDlet Testlet)
        {
        	super(true);
        	setFullScreenMode(true);
            myRooMIDlet = Testlet;
        }

        /**
         * Initialize self.
         */
        void init() {
        }

        /**
         * Cleanup and destroy.
         */
        void destroy() {
        }

        /*
         * Ask myRooMIDlet to paint itself
         */
        public void paint(Graphics g) {
            myRooMIDlet.paint(g);
        }

        /*
         * Ask myRooMIDlet to handle keyPressed events
         */
        protected void keyPressed(int keyCode) {
            myRooMIDlet.keyEvent(myRooMIDlet.KEY_PRESSED, keyCode);
        }

        /*
         * Ask myRooMIDlet to handle keyReleased events
         */
        protected void keyReleased(int keyCode) {
            myRooMIDlet.keyEvent(myRooMIDlet.KEY_RELEASED, keyCode);
        }

        /*
         * Ask myRooMIDlet to handle keyRepeated events
         */
        protected void keyRepeated(int keyCode) {
            myRooMIDlet.keyEvent(myRooMIDlet.KEY_REPEATED, keyCode);
        }
        
        
        
      

        /*
         * Ask myRooMIDlet to handle pointerDragged events
         */
        protected void pointerDragged(int x, int y) {
        }

        /*
         * Ask myRooMIDlet to handle pointerPressed events
         */
        protected void pointerPressed(int x, int y) 
        {
        	
        	
        	
        	
        	if(isPressed(60, 60+100, 0, 50, x, y))
        	{
        		myRooMIDlet.analysePress("pressed", _UP);
        		// myRooMIDlet.keyEvent(myRooMIDlet.KEY_PRESSED, keyCode);
        		// keyMoveRoo = keyForward;
        	}
        	else
        	if(isPressed(0, 0+60, 180, 180+60, x, y))
        	{
        		myRooMIDlet.analysePress("pressed", _LEFT);
        		// keyMoveRoo = keyLeft;
        	}
        	else
        	if(isPressed(240-60, 240, 180, 180+60, x, y))
        	{
        		myRooMIDlet.analysePress("pressed", _RIGHT);
        		 //keyMoveRoo = keyRight;
        	}
        	else
            if(isPressed(60, 60+100, 400, 400-60, x, y))
        	{
            	myRooMIDlet.analysePress("pressed", _LEFT);
            	// keyMoveRoo = keyBackward;
        	}
    		// TODO Auto-generated method stub
        }

        /*
         * Ask myRooMIDlet to handle pointerReleased events
         */
        protected void pointerReleased(int x, int y)
        {
        	if(isPressed(60, 60+100, 0, 50, x, y))
        	{
        		myRooMIDlet.analysePress("released", _UP);
        		// myRooMIDlet.keyEvent(myRooMIDlet.KEY_PRESSED, keyCode);
        		// keyMoveRoo = keyForward;
        	}
        	else
        	if(isPressed(0, 0+60, 180, 180+60, x, y))
        	{
        		myRooMIDlet.analysePress("released", _LEFT);
        		// keyMoveRoo = keyLeft;
        	}
        	else
        	if(isPressed(240-60, 240, 180, 180+60, x, y))
        	{
        		myRooMIDlet.analysePress("released", _RIGHT);
        		 //keyMoveRoo = keyRight;
        	}
        	else
            if(isPressed(60, 60+100, 400, 400-60, x, y))
        	{
            	myRooMIDlet.analysePress("released", _LEFT);
            	// keyMoveRoo = keyBackward;
        	}
        	
        	
        }
    }
}
