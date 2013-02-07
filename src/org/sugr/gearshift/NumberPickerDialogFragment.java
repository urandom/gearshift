package org.sugr.gearshift;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.text.method.ScrollingMovementMethod;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

public class NumberPickerDialogFragment extends DialogFragment {
    public interface OnDialogListener {
        public void onOkClick(NumberPickerDialogFragment dialog);
        public void onCancelClick(NumberPickerDialogFragment dialog);
    }
    protected int mInputSize = 10;
    protected int mInputPointer = -1;
    protected int mInput[] = new int [mInputSize];
    
    private final Button mNumbers[] = new Button [11];
    private ImageButton mDelete;
    private TextView mValue;

    private int mParentId;
    
    private OnDialogListener mListener;
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(
                new ContextThemeWrapper(getActivity(), android.R.style.Theme_Holo_Dialog));
        LayoutInflater inflater = getActivity().getLayoutInflater();
        
        View root = inflater.inflate(R.layout.dialog_number_picker, null);
                    
        builder.setView(root)
            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                    if (mListener != null)
                        mListener.onOkClick(NumberPickerDialogFragment.this);
                }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    NumberPickerDialogFragment.this.getDialog().cancel();
                    if (mListener != null)
                        mListener.onCancelClick(NumberPickerDialogFragment.this);
                }
            });
        
        mNumbers[0] = (Button) root.findViewById(R.id.button_0);
        mNumbers[1] = (Button) root.findViewById(R.id.button_1);
        mNumbers[2] = (Button) root.findViewById(R.id.button_2);
        mNumbers[3] = (Button) root.findViewById(R.id.button_3);
        mNumbers[4] = (Button) root.findViewById(R.id.button_4);
        mNumbers[5] = (Button) root.findViewById(R.id.button_5);
        mNumbers[6] = (Button) root.findViewById(R.id.button_6);
        mNumbers[7] = (Button) root.findViewById(R.id.button_7);
        mNumbers[8] = (Button) root.findViewById(R.id.button_8);
        mNumbers[9] = (Button) root.findViewById(R.id.button_9);
        mNumbers[10] = (Button) root.findViewById(R.id.button_00);
        
        mDelete = (ImageButton) root.findViewById(R.id.delete);
        mValue = (TextView) root.findViewById(R.id.value);
        mValue.setMovementMethod(new ScrollingMovementMethod());
        
        for (int i = 0; i < 11; i++) {
            mNumbers[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {         
                    doNumberPress(v);
                }
            });
            mNumbers[i].setText(i == 10 ? "00" : String.format("%d", i));
            mNumbers[i].setTag(R.id.numbers_key, Integer.valueOf(i));
        }
        
        mDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doDeletePress(v);
            }
        });
        mDelete.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                reset();
                return false;
            }
        });
        
        updateValue();
        
        return builder.create();
    }
    
    public NumberPickerDialogFragment setValue(int value) {
        int length = String.valueOf(value).length();
        int[] digits = new int [length];
        
        for (int i = 0; i < length; i++) {
            digits[i] = value % 10;
            value /= 10;
        }
        
        for (int i = length - 1; i >= 0; i--)
            addNumberToInput(digits[i]);
        
        updateValue();

        return this;
    }
    
    public long getValue() {
        return Long.parseLong(mValue.getText().toString());
    }
    
    public NumberPickerDialogFragment setParentId(int id) {
        mParentId = id;
        
        return this;
    }
    
    public int getParentId() {
        return mParentId;
    }
    
    public NumberPickerDialogFragment setListener(OnDialogListener listener) {
        mListener = listener;
        
        return this;
    }
    
    protected void addNumberToInput(int value) {
        if (value > -1 && value < 10) {
            if (mInputPointer == -1 && value == 0)
                return;
            
            if (mInputPointer < mInputSize - 1) {
                for (int i = mInputPointer; i >= 0; i--)
                    mInput[i + 1] = mInput[i];
                mInputPointer++;
                mInput[0] = value;
                updateValue();
            }
        }
    }
    
    protected void doNumberPress(View v) {
        int[] val = new int[2];

        Integer tag = (Integer) v.getTag(R.id.numbers_key);
        int length = 1;
        if (tag == 10) {
            val[0] = 0;
            val[1] = 0;
            length = 2;
        } else {
            val[0] = tag;
        }
        
        for (int j = 0; j < length; j++)
            addNumberToInput(val[j]);
    }
    
    protected void doDeletePress(View v) {
        if (mInputPointer >= 0) {
            for (int i = 0; i < mInputPointer; i++)
                mInput[i] = mInput[i + 1];
            mInput[mInputPointer] = 0;
            mInputPointer--;
            updateValue();
        }
        
    }
    
    protected void reset() {
        for (int i = 0; i < mInputSize; i++)
            mInput[i] = 0;
        mInputPointer = -1;
        updateValue();
    }
    
    protected void updateValue() {
        long value = 0;
        for (int i = 0; i <= mInputPointer; i++)
            value += mInput[i] * Math.pow(10.0, (double) i);
        
        if (mDelete != null)
            mDelete.setEnabled(mInputPointer > -1);

        if (mValue != null)
            mValue.setText(String.format("%d", value));
    }
}