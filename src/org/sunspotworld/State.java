/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sunspotworld;

import java.util.Vector;

/**
 *
 * @author Administrator
 */
public class State {
    private int swarmState = 1;
    private int electState = 1;
    private String send_msg = "";
    private Vector addrTable = new Vector();
    
    public synchronized void setState(int val){
        swarmState = val;
    }
    
    public synchronized int getState(){
        return swarmState;
    }  
    
    public synchronized void setElectStatus(int val){
        electState = val;
    }
    
    public synchronized int getElectStatus(){
        return electState;
    }  
    
    public synchronized void setsend_msg(String val){
        send_msg = val;
    }
    
    public synchronized String getsend_msg(){
        return send_msg;
    }    
    
    public synchronized void addTable(Long val){
        addrTable.addElement(val);
    }
    
    public synchronized long readTable(int i){
        return ((Long)addrTable.elementAt(i)).longValue();
    }
    
    public synchronized void removeTableEle(int i){
        addrTable.removeElementAt(i);
    }
    
    public boolean addrElection(long addr)
    {   
        long addr1=0;     
        for(int num=0;num<addrTable.size();num++){
            addr1 = readTable(num);
            if(addr1==addr) 
                return false;
        }
        addrTable.addElement(new Long(addr));
        sort();
        return true;
    }
    
    private void sort()
    {
        int size,mem,mem1;
        long addr1=0,addr2=0;
        Long temp;
        size=addrTable.size();
        for(mem=0;mem<(size-1);mem++){
            addr1=((Long)addrTable.elementAt(mem)).longValue();
            for(mem1=mem+1;mem1<size;mem1++){
                addr2=((Long)addrTable.elementAt(mem1)).longValue();
                if(addr2>addr1){
                    temp=(Long)addrTable.elementAt(mem);
                    addrTable.setElementAt((Long)addrTable.elementAt(mem1), mem);
                    addrTable.setElementAt(temp,mem1);                    
                }
            }
        }
    }
}
