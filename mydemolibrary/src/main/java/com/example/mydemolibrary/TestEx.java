package com.example.mydemolibrary;

import android.util.Log;

import com.yy.android.annotation.Export;

/**
 * Created by Administrator on 2017/2/25.
 */

@Export
public class TestEx extends Test implements ITest {
    @Override
    public String Test() {
        return null;
    }

    public class TestInner {
        int priority;
        public int hashCode() {
            return priority;
        }
    }

    public static enum Status {
        UNREAD,READED,SENDED,UNSEND;
    }
    public ITest getTest() {return this;}
}
