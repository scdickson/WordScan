package com.boilermake.wordscan;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/**
 * Created by sdickson on 8/8/13.
 */
public class WordListAdapter extends BaseAdapter
{
    Context context;
    LayoutInflater inflater;
    String[] words;

    public WordListAdapter(Context context, String[] words)
    {
        this.context = context;
        this.words = words;
    }
    
    public void setArray(String[] words)
    {
    	this.words = words;
    }

    public int getCount()
    {
        return words.length;
    }

    public String getItem(int position)
    {
        return words[position];
    }

    public long getItemId(int position)
    {
        return position;
    }

    public View getView(int position, View convertView, ViewGroup parent)
    {
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View itemView = inflater.inflate(R.layout.word_list_row, parent, false);
        LinearLayout word_item_layout = (LinearLayout) itemView.findViewById(R.id.word_item_layout);
        TextView txtWord = (TextView) itemView.findViewById(R.id.word_item);
        final String word = getItem(position);
        txtWord.setText(word);
        word_item_layout.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) 
			{
				System.out.println("Now get: " + word);
			}
		});
        
        return itemView;
    }
}
