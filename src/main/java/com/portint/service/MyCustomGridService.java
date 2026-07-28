package com.portint.service;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;

public class MyCustomGridService implements IMyCustomGridService {
    private final IGrid grid;

    public MyCustomGridService(IGrid grid) {
        this.grid = grid;
    }
}
