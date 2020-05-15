package tools.concurrency;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.source.SourceSection;
import som.compiler.MixinDefinition;
import som.compiler.Variable;
import som.interpreter.SArguments;
import som.interpreter.Types;
import som.interpreter.actors.EventualMessage;
import som.vm.VmSettings;

import tools.debugger.entities.Marker;

import javax.xml.bind.annotation.XmlElementDecl;

public class RecordAssignment {

    enum AssignmentTypes {
        GLOBAL(Marker.GLOBAL_ASSIGNMENT),
        NONGLOBAL(Marker.NON_GLOBAL_ASSIGNMENT);

        private final byte Id;
        AssignmentTypes(byte id){
            this.Id = id;
        }

        public byte getId() {
            return Id;
        }
    }


    private static Boolean doRecord(){
        return VmSettings.KOMPOS_TRACING && Thread.currentThread() instanceof TracingActivityThread;
    }

    //todo make one function?

    public static void recordAssignment(final boolean expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            KomposTrace.assignment(AssignmentTypes.NONGLOBAL, var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment( final Long expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            KomposTrace.assignment(AssignmentTypes.NONGLOBAL, var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment(final Object expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){
            KomposTrace.assignment(AssignmentTypes.NONGLOBAL, var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordAssignment( final Double expValue, SourceSection assignmentSource, Variable.Local var){
        if(doRecord()){

           KomposTrace.assignment(AssignmentTypes.NONGLOBAL, var.source, assignmentSource, Types.toDebuggerString(expValue));

        }
    }

    public static void recordGlobalAssignment(final Object expValue, SourceSection assignmentSource, MixinDefinition.SlotDefinition slot){
        if(doRecord() && !slot.isImmutable()){
            KomposTrace.assignment(AssignmentTypes.GLOBAL, slot.getSourceSection(), assignmentSource, Types.toDebuggerString(expValue));
        }
    }


}
