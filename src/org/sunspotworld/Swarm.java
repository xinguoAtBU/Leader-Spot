
package org.sunspotworld;

import com.sun.spot.util.Utils;
import com.sun.spot.peripheral.Spot;
import com.sun.spot.peripheral.TimeoutException;
import com.sun.spot.peripheral.radio.IProprietaryRadio;
import com.sun.spot.peripheral.radio.IRadioPolicyManager;
import com.sun.spot.resources.Resources;
//import com.sun.spot.resources.transducers.ILed;
import com.sun.spot.resources.transducers.ISwitch;
import com.sun.spot.resources.transducers.LEDColor;
import com.sun.spot.io.j2me.radiogram.Radiogram;
import com.sun.spot.io.j2me.radiogram.RadiogramConnection;
import com.sun.spot.peripheral.radio.RadioFactory;
import com.sun.spot.resources.transducers.ISwitchListener;
import com.sun.spot.resources.transducers.SwitchEvent;
import com.sun.spot.service.BootloaderListenerService;
import com.sun.spot.util.IEEEAddress;
import java.io.IOException;
import javax.microedition.io.Connector;
import javax.microedition.io.Datagram;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;


public class Swarm extends MIDlet {
   // private static final int INITIAL_CHANNEL_NUMBER = IProprietaryRadio.DEFAULT_CHANNEL;
    private static final int BROADCAST_CHANNEL = 15;   
//    private static final short PAN_ID               = IRadioPolicyManager.DEFAULT_PAN_ID;
    private static final String BROADCAST_PORT      = "62";
    private static final int PACKET_INTERVAL        = 2000;
    private static final int POWER = 16; // Start with max transmit power
    private static final int TIMEOUT_LEADER_ALIVE = 4500;      // ms
    private static final int ELECT_INITIAL_TIME = 6500;        // ms
    private static final int INFECT_PERIOD = 3000;        // ms
    private static final int STATUS_FIND_LEADER = 1;
    private static final int STATUS_LEADED = 2; 
    private static final int STATUS_LEADING = 3;   
    private static final int STATE_UNLOCK = 1;
    private static final int STATE_INFECT = 2; 
    private static final int STATE_LOCK = 3; 
    
    private static RadiogramConnection rxConnection = null;
    private static Radiogram rdg = null;
    private static RadiogramConnection txConnection = null;
    private static Radiogram xdg = null;
    
    private ISwitch sw1 = (ISwitch)Resources.lookup(ISwitch.class, "SW1");
    private ISwitch sw2 = (ISwitch)Resources.lookup(ISwitch.class, "SW2");   
    private LED led = new LED();
    private State state = new State();
    
    private boolean ifLeader = true;
    private long timerLeaderAliveStart = 0;
    private long electTimeOut = 0;
    private long infectTime = 0;
    private int spotNum = 1;

    private void initialize() {
        IRadioPolicyManager rpm = Spot.getInstance().getRadioPolicyManager();
        rpm.setOutputPower(POWER - 32);
        rpm.setChannelNumber(BROADCAST_CHANNEL);
        
        try {
            long leaderAddr = RadioFactory.getRadioPolicyManager().getIEEEAddress();
            System.out.println("Our radio address = " + IEEEAddress.toDottedHex(leaderAddr));
            
            // Connection used for receiving beacon transmissions
            rxConnection = (RadiogramConnection) Connector.open("radiogram://:" + BROADCAST_PORT);
            rdg = (Radiogram) rxConnection.newDatagram(rxConnection.getMaximumLength());
            
            // Connection used for relaying transmission data back to base
            txConnection = (RadiogramConnection) Connector.open("radiogram://broadcast:" + BROADCAST_PORT);
            xdg = (Radiogram) txConnection.newDatagram(txConnection.getMaximumLength());
            
            //initialize address table
            state.addTable(new Long(leaderAddr));
            
            // blink to confirm successful connection setup!
            led.blinkLEDall(LEDColor.GREEN);
            
        } catch (IOException ex) {
            //blink red upon failure. :(
            led.setLEDall( LEDColor.ORANGE);            
            System.err.println("Could not open radiogram broadcast connection!");
            System.err.println(ex);
        }
    }

    /**
     * Main application run loop.
     */
    private void run() {     
        new Thread() {
            public void run () {
                recvLoop();
            }
        }.start();                      // spawn a thread to receive packets

        new Thread() {
            public void run () {
                xmitLoop();
            }
        }.start(); 
        
        addSwitch();
    }

    private void recvLoop () {
        electTimeOut = System.currentTimeMillis();
        while (true) {
            try {
                
                rdg.reset();
                rxConnection.receive(rdg);           // listen for a packet
                spotNum++;
                electRec();
                swarmRec();                
                
            } catch (IOException ex) {
                System.err.println("Could not receive!");
                System.err.println(ex);
            }
            
        }         
    }
        
    private void xmitLoop (){
        infectTime = System.currentTimeMillis();
        while (true) {
            try {
                    long nextTime = System.currentTimeMillis() + PACKET_INTERVAL;
                    xdg.reset();       
                    
                    electTrx();
                    swarmTrx();
                    
                    long delay = (nextTime - System.currentTimeMillis());
                    if (delay > 0) {
                        Utils.sleep(delay);
                    }
                    
            } catch (Exception ex) {
                System.err.println("Could not broadcast!");
                System.err.println(ex);
                return;
            }
        }
    }
    
    private void electRec() throws IOException{        
        if(state.getElectStatus() == STATUS_FIND_LEADER){
            if(state.addrElection(rdg.getAddressAsLong())){
                electTimeOut = System.currentTimeMillis();
            }
        }else if(state.getElectStatus() == STATUS_LEADED){
            long getAddr = rdg.getAddressAsLong();
            if(getAddr > state.readTable(0)){
                state.addrElection(getAddr);
            }else if(getAddr == state.readTable(0)){
                timerLeaderAliveStart = System.currentTimeMillis();
            }               
        }else if(state.getElectStatus() == STATUS_LEADING){
            long getAddr = rdg.getAddressAsLong();
            if(getAddr > state.readTable(0)){
                state.addrElection(getAddr);
                timerLeaderAliveStart = System.currentTimeMillis();
                state.setElectStatus(STATUS_LEADED);
            }
        }
    }
    
                    
    private void swarmRec() throws IOException{
        String receive_msg = rdg.readUTF();
        System.out.println("message: " + receive_msg);
        if(rdg.getAddressAsLong() == state.readTable(0)){
            if(receive_msg.equals("red")){
                led.setLEDall(LEDColor.RED);
                state.setState(STATE_LOCK);
               //setsend_msg("red");                        
            }else if(receive_msg.equals("blue")){
                led.setLEDall(LEDColor.BLUE);
                state.setState(STATE_LOCK);
                //state.setsend_msg("blue");                        
            }
        }else if(receive_msg.equals("infect") && (STATE_UNLOCK == state.getState())){
            state.setState(STATE_INFECT);
        }       
    }
    
    private void electTrx() throws IOException{
        if(state.getElectStatus() == STATUS_FIND_LEADER){
            if((System.currentTimeMillis() - electTimeOut) > ELECT_INITIAL_TIME){
                long Addr = state.readTable(0);
                if(Addr != RadioFactory.getRadioPolicyManager().getIEEEAddress()){          
                    state.setElectStatus(STATUS_LEADED);
                    timerLeaderAliveStart = System.currentTimeMillis();
                }else{
                    state.setElectStatus(STATUS_LEADING);
                }                                 
            }
            led.blinkLEDall(LEDColor.GREEN);
            txConnection.send(xdg);
            
        }else if(state.getElectStatus() == STATUS_LEADED){
            if((System.currentTimeMillis() - timerLeaderAliveStart) > TIMEOUT_LEADER_ALIVE){
                state.removeTableEle(0);
                if(state.readTable(0) == RadioFactory.getRadioPolicyManager().getIEEEAddress()){
                    state.setElectStatus(STATUS_LEADING);  
                    //state.setState(STATE_UNLOCK);
                }else{
                    timerLeaderAliveStart = System.currentTimeMillis();
                }                              
            }
        }
    }

    
    private void swarmTrx() throws IOException{
        //System.out.println("state: "+state.getState()+" elect: "+state.getElectStatus());
        if(state.getElectStatus() == STATUS_LEADED){//as a non-Leader
            //xdg.writeInt(1);
            if(STATE_UNLOCK == state.getState()){//unlocked
                led.tilt();
                xdg.writeUTF("");
                //System.out.println("state: " + state.getState());
                
            }else if(STATE_INFECT == state.getState()){ //infected
                //send_msg = "infect";
                //if((System.currentTimeMillis() - infectTime) > INFECT_PERIOD){
                    xdg.writeUTF("infect");
                    //infectTime = System.currentTimeMillis();  
                    //System.out.println("infect");
                //} 
                //System.out.println("no");
                
                led.setLEDall(LEDColor.YELLOW);                
            }else if(3 == state.getState()){//locked
                //if(!state.getsend_msg().equals("")) {
                    //xdg.writeUTF(state.getsend_msg());
                //}
                xdg.writeUTF("");
                //state.setsend_msg("");
            } 
            txConnection.send(xdg);
            
        }else if(state.getElectStatus() == STATUS_LEADING){//as a Leader
            if(STATE_INFECT == state.getState()){
                led.displayNum(spotNum, true);
            }else if(STATE_UNLOCK == state.getState()){
                led.displayNum(spotNum, false);
            }
            spotNum = 1;
            xdg.writeUTF(state.getsend_msg());
            txConnection.send(xdg);
            state.setsend_msg("");
            
        }
    }
    
    private void addSwitch(){  
        ISwitchListener buttonA_lis = new ISwitchListener(){
            public  void switchPressed(SwitchEvent se) {
                if(1 == state.getState()) state.setState(3);
                else if(3 == state.getState()) state.setState(1);
                System.out.println("button A: "+ state.getState());
            }

            public void switchReleased(SwitchEvent se) {
            }           
        }; 
        sw1.addISwitchListener(buttonA_lis);  
        
        ISwitchListener button2_lis = new ISwitchListener(){
            public  void switchPressed(SwitchEvent se) {
                if(state.getElectStatus()==STATUS_LEADING){
                    if(STATE_INFECT == state.getState()) 
                        state.setState(STATE_UNLOCK);
                    try {
                        if(led.tiltMsg() > 0) 
                            state.setsend_msg("red");
                        else 
                            state.setsend_msg("blue");
                    } catch (IOException ex) {
                    }
                }
                else if(STATE_UNLOCK == state.getState()){
                    state.setState(STATE_INFECT);
                }
                System.out.println("button B");
            }

            public void switchReleased(SwitchEvent se) {               
            }           
        }; 
        sw2.addISwitchListener(button2_lis); 
    }
    
    protected void startApp() throws MIDletStateChangeException {
	// Listen for downloads/commands over USB connection        
        BootloaderListenerService.getInstance().start();
        initialize();       
        run();
    }

    /**
     * This will never be called by the Squawk VM.
     */
    protected void pauseApp() {
        // This will never be called by the Squawk VM
    }

    /**
     * Called if the MIDlet is terminated by the system.
     * @param unconditional If true the MIDlet must cleanup and release all resources.
     * @throws javax.microedition.midlet.MIDletStateChangeException
     */
    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
    }

}
