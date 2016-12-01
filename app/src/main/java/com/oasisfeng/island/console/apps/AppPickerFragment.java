package com.oasisfeng.island.console.apps;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.oasisfeng.island.databinding.AppListBinding;
import com.oasisfeng.island.engine.IslandManager;
import com.oasisfeng.island.model.AppListViewModel;

/**
 * Picker UI to add installed apps to Island
 *
 * Created by Oasis on 2016/6/24.
 */
public class AppPickerFragment extends Fragment {

	@Override public void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mViewModel = new AppListViewModel(getActivity(), IslandManager.NULL);
	}

	@Nullable @Override public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		setHasOptionsMenu(true);
		mBinding = AppListBinding.inflate(inflater, container, false);
		mBinding.setApps(mViewModel);
		mBinding.appList.setLayoutManager(new LinearLayoutManager(getActivity()));
		return mBinding.getRoot();
	}

	private AppListViewModel mViewModel;
	private AppListBinding mBinding;
}
