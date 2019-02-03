package com.example.juc;

/**
 * javap工具查看同步代码块。
 */
public class JavaPTest {

    public synchronized void test1() {

    }

    public void test2() {

        synchronized (this) {


        }

    }
    /**
     * xinleigao:jucdemo] $javap -v target/classes/com/example/juc/JavaPTest.class
     Classfile /gao/repo/jucdemo/target/classes/com/example/juc/JavaPTest.class
     Last modified 2019-2-3; size 452 bytes
     MD5 checksum 284ff780477c118d9cd86f27c56c3a27
     Compiled from "JavaPTest.java"
     public class com.example.juc.JavaPTest
     minor version: 0
     major version: 49
     flags: ACC_PUBLIC, ACC_SUPER
     Constant pool:
     #1 = Methodref          #3.#15         // java/lang/Object."<init>":()V
     #2 = Class              #16            // com/example/juc/JavaPTest
     #3 = Class              #17            // java/lang/Object
     #4 = Utf8               <init>
     #5 = Utf8               ()V
     #6 = Utf8               Code
     #7 = Utf8               LineNumberTable
     #8 = Utf8               LocalVariableTable
     #9 = Utf8               this
     #10 = Utf8               Lcom/example/juc/JavaPTest;
     #11 = Utf8               test1
     #12 = Utf8               test2
     #13 = Utf8               SourceFile
     #14 = Utf8               JavaPTest.java
     #15 = NameAndType        #4:#5          // "<init>":()V
     #16 = Utf8               com/example/juc/JavaPTest
     #17 = Utf8               java/lang/Object
     {
     public com.example.juc.JavaPTest();
     descriptor: ()V
     flags: ACC_PUBLIC
     Code:
     stack=1, locals=1, args_size=1
     0: aload_0
     1: invokespecial #1                  // Method java/lang/Object."<init>":()V
     4: return
     LineNumberTable:
     line 6: 0
     LocalVariableTable:
     Start  Length  Slot  Name   Signature
     0       5     0  this   Lcom/example/juc/JavaPTest;

     public synchronized void test1();
     descriptor: ()V
     flags: ACC_PUBLIC, ACC_SYNCHRONIZED
     Code:
     stack=0, locals=1, args_size=1
     0: return
     LineNumberTable:
     line 11: 0
     LocalVariableTable:
     Start  Length  Slot  Name   Signature
     0       1     0  this   Lcom/example/juc/JavaPTest;

     public void test2();
     descriptor: ()V
     flags: ACC_PUBLIC
     Code:
     stack=2, locals=3, args_size=1
     0: aload_0
     1: dup
     2: astore_1
     3: monitorenter
     4: aload_1
     5: monitorexit
     6: goto          14
     9: astore_2
     10: aload_1
     11: monitorexit
     12: aload_2
     13: athrow
     14: return
     Exception table:
     from    to  target type
     4     6     9   any
     9    12     9   any
     LineNumberTable:
     line 16: 0
     line 19: 4
     line 21: 14
     LocalVariableTable:
     Start  Length  Slot  Name   Signature
     0      15     0  this   Lcom/example/juc/JavaPTest;
     }
     SourceFile: "JavaPTest.java"
     */
}
