package com.yin.util;

public class AtmHelp {
    public static float computerAtm(float dangji,float dangji_index,float dangzhou,float dangzhou_index){
        float aoteman_index = ((dangji - dangzhou) - (dangji_index - dangzhou_index))/2;
        return aoteman_index;
    }
}
