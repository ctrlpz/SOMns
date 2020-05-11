package tools.concurrency;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import som.compiler.Variable;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;

public class RecordAssignment {
    private static Boolean doRecord(){
        return VmSettings.KOMPOS_TRACING && Thread.currentThread() instanceof TracingActivityThread;
    }


    public static void recordAssignment(final VirtualFrame frame, final boolean expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            KomposTrace.assignment( var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment(final VirtualFrame frame, final Long expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            KomposTrace.assignment( var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment(final VirtualFrame frame, final Object expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            System.out.println("Types: " + Types.toDebuggerString(expValue));
            KomposTrace.assignment( var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment(final VirtualFrame frame, final Double expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){

           KomposTrace.assignment( var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }
}
