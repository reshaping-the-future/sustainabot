package ac.robinson.sustainabot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;

import com.facebook.common.util.UriUtil;
import com.facebook.drawee.view.SimpleDraweeView;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

public class IconActivity extends AppCompatActivity {
	private GridAdapter mGridAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_icon);
		mGridAdapter = new GridAdapter(getMediaItemList());
		GridView gridView = findViewById(R.id.grid_view);
		gridView.setEmptyView(findViewById(R.id.empty_grid_view));
		gridView.setAdapter(mGridAdapter);
		gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mGridAdapter.onClick(position);
			}
		});
	}

	private ArrayList<Integer> getMediaItemList() {
		ArrayList<Integer> items = new ArrayList<>();
		items.add(R.drawable.sustainabot_ic_accessibility_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_arrow_back_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_arrow_forward_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_home_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_arrow_upward_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_arrow_downward_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_done_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_clear_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_place_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_tag_faces_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_favorite_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_brightness_1_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_cloud_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_error_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_notifications_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_play_arrow_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_stop_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_star_blue_grey_500_48dp);
		items.add(R.drawable.sustainabot_ic_thumb_up_blue_grey_500_48dp);
		return items;
	}

	private class GridAdapter extends BaseAdapter {
		final ArrayList<Integer> mItems;

		private GridAdapter(ArrayList<Integer> photos) {
			mItems = new ArrayList<>();
			mItems.addAll(photos);
		}

		@Override
		public int getCount() {
			return mItems.size();
		}

		@Override
		public Object getItem(int position) {
			return mItems.get(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view = convertView;
			PhotoViewHolder viewHolder;
			if (view == null) {
				view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_thumbnail, parent, false);
				viewHolder = new PhotoViewHolder();
				viewHolder.image = view.findViewById(R.id.gallery_image);
				view.setTag(viewHolder);
			} else {
				viewHolder = (PhotoViewHolder) view.getTag();
			}
			Uri uri = new Uri.Builder().scheme(UriUtil.LOCAL_RESOURCE_SCHEME) // "res"
					.path(String.valueOf(mItems.get(position))).build();
			viewHolder.image.setImageURI(uri);
			return view;
		}

		void onClick(int position) {
			String fileName = "processed-icon.png";
			Bitmap icon = BitmapFactory.decodeResource(getResources(), mItems.get(position));
			if (saveToInternalStorage(IconActivity.this, icon, fileName)) {
				Intent result = new Intent();
				result.putExtra(MainActivity.RESULT_IMAGE, fileName);
				setResult(Activity.RESULT_OK, result);
				finish();
			} else {
				Log.d("IconActivity", "ERROR SAVING ICON FILE");
			}
		}
	}

	private boolean saveToInternalStorage(Context context, Bitmap image, String fileName) {
		FileOutputStream fileOutputStream = null;
		try {
			fileOutputStream = context.openFileOutput(fileName, MODE_PRIVATE);
			image.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream); // PNG as is just b/w dots
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} finally {
			if (fileOutputStream != null) {
				try {
					fileOutputStream.close();
				} catch (IOException ignored) {
				}
			}
		}
	}

	private static class PhotoViewHolder {
		SimpleDraweeView image;
	}
}
