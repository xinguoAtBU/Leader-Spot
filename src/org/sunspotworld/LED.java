/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld;

import com.sun.spot.resources.Resources;
import com.sun.spot.resources.transducers.IAccelerometer3D;
import com.sun.spot.resources.transducers.ITriColorLEDArray;
import com.sun.spot.resources.transducers.LEDColor;
import com.sun.spot.util.Utils;
import java.io.IOException;

/**
 *
 * @author Administrator
 */
public class LED {
    private ITriColorLEDArray leds = (ITriColorLEDArray) Resources.lookup(ITriColorLEDArray.class);
    private IAccelerometer3D accel = (IAccelerometer3D) Resources.lookup(IAccelerometer3D.class);
    private static final int LED_ON = 10;
    private static final int CALI_X = 10;
    
    public  void blinkLEDall(LEDColor color) {
        leds.setColor(color);
        leds.setOn();
        Utils.sleep(LED_ON);
        leds.setOff();
    }
    
    public void setLEDall(LEDColor color) {
        leds.setColor(color);
        leds.setOn();
    }
    
    public void displayNum(int val, boolean infect) throws IOException {
        if(infect){
            for (int i = 0, mask = 1; i < 7; i++, mask <<= 1) {
                leds.getLED(7-i).setColor(LEDColor.YELLOW);
                leds.getLED(7-i).setOn((val & mask) != 0);
            } 
        }else{
            int Xtilt = (int) accel.getTiltX();
            if(Xtilt > 0)
                for (int i = 0, mask = 1; i < 7; i++, mask <<= 1) {
                    leds.getLED(7-i).setColor(LEDColor.RED);
                    leds.getLED(7-i).setOn((val & mask) != 0);
                }
            else
               for (int i = 0, mask = 1; i < 7; i++, mask <<= 1) {
                    leds.getLED(7-i).setColor(LEDColor.BLUE);
                    leds.getLED(7-i).setOn((val & mask) != 0);     
                }
        }
        
    }
    
    public void tilt() throws IOException {        
        int Xtilt = (int) accel.getTiltX();
        System.out.println("xtilt: " + Xtilt );
        if(Xtilt > 0) leds.setColor(LEDColor.RED);
        else leds.setColor(LEDColor.BLUE);
        leds.setOn();
    }
    
    public int tiltMsg() throws IOException {  
        int Xtilt = (int) accel.getTiltX();
        System.out.println("xtilt: " + Xtilt);
        return Xtilt;
    }
}
