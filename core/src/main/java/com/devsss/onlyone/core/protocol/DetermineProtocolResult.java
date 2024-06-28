package com.devsss.onlyone.core.protocol;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

@Data
@NoArgsConstructor
public class DetermineProtocolResult {

    private ProtocolType protocolType;

    private ArrayList<Integer> usedData;

    private String usedDataStr;

    public String getUsedDataStr() {
        if (usedDataStr == null) {
            StringBuilder lineSb = new StringBuilder();
            usedData.forEach( i -> lineSb.append((char) i.intValue()));
            usedDataStr = lineSb.toString();
        }
        return usedDataStr;
    }
}
