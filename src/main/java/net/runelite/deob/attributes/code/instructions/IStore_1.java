package net.runelite.deob.attributes.code.instructions;

import net.runelite.deob.attributes.code.Instruction;
import net.runelite.deob.attributes.code.InstructionType;
import net.runelite.deob.attributes.code.Instructions;
import net.runelite.deob.attributes.code.instruction.types.LVTInstruction;
import net.runelite.deob.execution.Frame;
import net.runelite.deob.execution.InstructionContext;
import net.runelite.deob.execution.Stack;
import net.runelite.deob.execution.StackContext;
import net.runelite.deob.execution.Type;
import net.runelite.deob.execution.VariableContext;
import net.runelite.deob.execution.Variables;

import java.io.IOException;

public class IStore_1 extends Instruction implements LVTInstruction
{
	public IStore_1(Instructions instructions, InstructionType type, int pc)
	{
		super(instructions, type, pc);
	}
	
	public IStore_1(Instructions instructions)
	{
		super(instructions, InstructionType.ISTORE_1, -1);
	}

	@Override
	public void execute(Frame frame)
	{
		InstructionContext ins = new InstructionContext(this, frame);
		Stack stack = frame.getStack();
		Variables variables = frame.getVariables();
		
		StackContext value = stack.pop();
		assert value.getType().equals(new Type(int.class.getName()));
		ins.pop(value);
		
		variables.set(1, new VariableContext(ins, value));
		
		frame.addInstructionContext(ins);
	}
	
	@Override
	public int getVariableIndex()
	{
		return 1;
	}
	
	@Override
	public Instruction setVariableIndex(int idx)
	{
		return new IStore(this.getInstructions(), idx);
	}

	@Override
	public boolean store()
	{
		return true;
	}
}
