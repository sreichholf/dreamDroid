package net.reichholf.dreamdroid.adapter.recyclerview;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import net.reichholf.dreamdroid.R;
import net.reichholf.dreamdroid.helpers.ExtendedHashMap;
import net.reichholf.dreamdroid.helpers.enigma2.Movie;

import java.util.ArrayList;

/**
 * Created by Stephan on 05.05.2015.
 */
public class MovieAdapter extends BaseAdapter<MovieAdapter.MovieViewHolder> {

	public MovieAdapter(ArrayList<ExtendedHashMap> data) {
		super(data);
	}

	@Override
	public MovieViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
		LayoutInflater inflater = LayoutInflater.from(parent.getContext());
		View itemView = inflater.inflate(R.layout.movie_list_item, parent, false);
		return new MovieViewHolder(itemView);
	}

	@Override
	public void onBindViewHolder(MovieViewHolder holder, int position) {
		ExtendedHashMap movie = mData.get(position);
		if(movie != null){
			holder.title.setText(movie.getString(Movie.KEY_TITLE));
			holder.service.setText(movie.getString(Movie.KEY_SERVICE_NAME));
			holder.fileSize.setText(movie.getString(Movie.KEY_FILE_SIZE_READABLE));
			holder.eventStart.setText(movie.getString(Movie.KEY_TIME_READABLE));
			holder.eventDuration.setText(movie.getString(Movie.KEY_LENGTH));
		}
	}


	//		mAdapter = new SimpleAdapter(getAppCompatActivity(), mMapList, R.layout.movie_list_item, new String[]{
//				Movie.KEY_TITLE, Movie.KEY_SERVICE_NAME, Movie.KEY_FILE_SIZE_READABLE, Movie.KEY_TIME_READABLE,
//				Movie.KEY_LENGTH}, new int[]{R.id.movie_title, R.id.service_name, R.id.file_size, R.id.event_start,
//				R.id.event_duration});
	public class MovieViewHolder extends RecyclerView.ViewHolder {
		public TextView title;
		public TextView service;
		public TextView fileSize;
		public TextView eventStart;
		public TextView eventDuration;

		public MovieViewHolder(View itemView) {
			super(itemView);
			title = (TextView) itemView.findViewById(R.id.movie_title);
			service = (TextView) itemView.findViewById(R.id.service_name);
			fileSize = (TextView) itemView.findViewById(R.id.file_size);
			eventStart = (TextView) itemView.findViewById(R.id.event_start);
			eventDuration = (TextView) itemView.findViewById(R.id.event_duration);
		}
	}
}
