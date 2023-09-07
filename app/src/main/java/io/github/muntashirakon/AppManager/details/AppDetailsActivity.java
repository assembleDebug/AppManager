// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.details;

import android.annotation.UserIdInt;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.UserHandleHidden;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.os.BundleCompat;
import androidx.core.os.ParcelCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import java.util.Objects;

import io.github.muntashirakon.AppManager.BaseActivity;
import io.github.muntashirakon.AppManager.R;
import io.github.muntashirakon.AppManager.apk.ApkSource;
import io.github.muntashirakon.AppManager.details.info.AppInfoFragment;
import io.github.muntashirakon.AppManager.intercept.IntentCompat;
import io.github.muntashirakon.AppManager.logs.Log;
import io.github.muntashirakon.AppManager.main.MainActivity;
import io.github.muntashirakon.AppManager.misc.AdvancedSearchView;
import io.github.muntashirakon.AppManager.utils.UIUtils;
import io.github.muntashirakon.io.Path;
import io.github.muntashirakon.io.Paths;

public class AppDetailsActivity extends BaseActivity {
    public static final String ALIAS_APP_INFO = "io.github.muntashirakon.AppManager.details.AppInfoActivity";

    private static final String EXTRA_PACKAGE_NAME = "pkg";
    private static final String EXTRA_APK_SOURCE = "src";
    private static final String EXTRA_USER_HANDLE = "user";
    private static final String EXTRA_BACK_TO_MAIN = "main";

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, userId);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull String packageName, @UserIdInt int userId,
                                   boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_PACKAGE_NAME, packageName);
        intent.putExtra(AppDetailsActivity.EXTRA_USER_HANDLE, userId);
        intent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull ApkSource apkSource, boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        intent.putExtra(AppDetailsActivity.EXTRA_APK_SOURCE, apkSource);
        intent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Path apkPath, boolean backToMainPage) {
        return getIntent(context, apkPath.getUri(), apkPath.getType(), backToMainPage);
    }

    @NonNull
    public static Intent getIntent(@NonNull Context context, @NonNull Uri apkPath, @Nullable String mimeType, boolean backToMainPage) {
        Intent intent = new Intent(context, AppDetailsActivity.class);
        if (mimeType != null) {
            intent.setDataAndType(apkPath, mimeType);
        } else {
            intent.setData(apkPath);
        }
        intent.putExtra(AppDetailsActivity.EXTRA_BACK_TO_MAIN, backToMainPage);
        return intent;
    }

    public AppDetailsViewModel model;
    public AdvancedSearchView searchView;

    private ViewPager mViewPager;
    private TypedArray mTabTitleIds;
    private Fragment[] mTabFragments;

    private boolean mBackToMainPage;
    @Nullable
    private String mPackageName;
    @Nullable
    private ApkSource mApkSource;
    @Nullable
    private String mApkType;
    @UserIdInt
    private int mUserId;

    @Override
    protected void onAuthenticated(@Nullable Bundle savedInstanceState) {
        setContentView(R.layout.activity_app_details);
        setSupportActionBar(findViewById(R.id.toolbar));
        setTitle("…");
        model = new ViewModelProvider(this).get(AppDetailsViewModel.class);
        // Restore instance state
        SavedState ss = savedInstanceState != null ? BundleCompat.getParcelable(savedInstanceState, "ss", SavedState.class) : null;
        if (ss != null) {
            mBackToMainPage = ss.mBackToMainPage;
            mPackageName = ss.mPackageName;
            mApkSource = ss.mApkSource;
            mApkType = ss.mApkType;
            mUserId = ss.mUserId;
        } else {
            Intent intent = getIntent();
            Uri uri = IntentCompat.getDataUri(intent);
            mBackToMainPage = intent.getBooleanExtra(EXTRA_BACK_TO_MAIN, mBackToMainPage);
            // Package name needs to be sanitized since it's also a file
            mPackageName = Paths.sanitizeFilename(intent.getStringExtra(EXTRA_PACKAGE_NAME));
            mApkSource = uri != null
                    ? ApkSource.getApkSource(uri, intent.getType())
                    : IntentCompat.getParcelableExtra(intent, EXTRA_APK_SOURCE, ApkSource.class);
            mApkType = intent.getType();
            mUserId = intent.getIntExtra(EXTRA_USER_HANDLE, UserHandleHidden.myUserId());
        }
        model.setUserId(mUserId);
        // Initialize tabs
        mTabTitleIds = getResources().obtainTypedArray(R.array.TAB_TITLES);
        mTabFragments = new Fragment[mTabTitleIds.length()];
        if (mPackageName == null && mApkSource == null) {
            UIUtils.displayLongToast(R.string.empty_package_name);
            finish();
            return;
        }
        // Set search
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowCustomEnabled(true);
            searchView = UIUtils.setupAdvancedSearchView(actionBar, null);
        }
        FragmentManager fragmentManager = getSupportFragmentManager();
        mViewPager = findViewById(R.id.pager);

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        tabLayout.setupWithViewPager(mViewPager);
        final AlertDialog progressDialog = UIUtils.getProgressDialog(this, getText(R.string.loading));
        if (mPackageName == null) {
            // Display progress dialog only for external apk files
            progressDialog.show();
        }
        // Set tabs
        mViewPager.setAdapter(new AppDetailsFragmentPagerAdapter(fragmentManager));
        // Load package info
        (mPackageName != null
                ? model.setPackage(mPackageName)
                : model.setPackage(Objects.requireNonNull(mApkSource))
        ).observe(this, packageInfo -> {
            progressDialog.dismiss();
            if (packageInfo == null) {
                UIUtils.displayShortToast(R.string.failed_to_fetch_package_info);
                if (!isDestroyed()) {
                    finish();
                }
                return;
            }
            // Set title
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            // Set title as the package label
            setTitle(applicationInfo.loadLabel(getPackageManager()));
        });
        // Check for the existence of package
        model.getIsPackageExistLiveData().observe(this, isPackageExist -> {
            if (!isPackageExist) {
                if (!model.isExternalApk()) {
                    UIUtils.displayShortToast(R.string.app_not_installed);
                }
                finish();
            }
        });
        // Set subtitle as the username if more than one user exists
        model.getUserInfo().observe(this, userInfo -> getSupportActionBar()
                .setSubtitle(getString(R.string.user_profile_with_id, userInfo.name, userInfo.id)));
        // Check for package changes
        model.isPackageChanged().observe(this, isPackageChanged -> {
            if (isPackageChanged && model.isPackageExist()) {
                loadTabs();
            }
        });
    }

    static class SavedState implements Parcelable {
        private boolean mBackToMainPage;
        @Nullable
        private String mPackageName;
        @Nullable
        private ApkSource mApkSource;
        @Nullable
        private String mApkType;
        private int mUserId;

        protected SavedState() {
        }

        public SavedState(Parcel source) {
            mBackToMainPage = ParcelCompat.readBoolean(source);
            mPackageName = source.readString();
            mApkSource = ParcelCompat.readParcelable(source, ApkSource.class.getClassLoader(), ApkSource.class);
            mApkType = source.readString();
            mUserId = source.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            ParcelCompat.writeBoolean(dest, mBackToMainPage);
            dest.writeString(mPackageName);
            dest.writeParcelable(mApkSource, flags);
            dest.writeString(mApkType);
            dest.writeInt(mUserId);
        }

        public static final Creator<SavedState> CREATOR = new ClassLoaderCreator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                return new SavedState(in);
            }

            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        if (mApkSource != null || mPackageName != null) {
            SavedState ss = new SavedState();
            ss.mBackToMainPage = mBackToMainPage;
            ss.mPackageName = mPackageName;
            ss.mApkSource = mApkSource;
            ss.mApkType = mApkType;
            ss.mUserId = mUserId;
            outState.putParcelable("ss", ss);
        }
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        final int id = item.getItemId();
        if (id == android.R.id.home) {
            if (mBackToMainPage) {
                Intent intent = new Intent(this, MainActivity.class);
                startActivity(intent);
            }
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadTabs() {
        @AppDetailsFragment.Property int id = mViewPager.getCurrentItem();
        Log.d("ADA - " + mTabTitleIds.getText(id), "isPackageChanged called");
        for (int i = 0; i < mTabTitleIds.length(); ++i) model.load(i);
    }

    // For tab layout
    private class AppDetailsFragmentPagerAdapter extends FragmentPagerAdapter {
        AppDetailsFragmentPagerAdapter(@NonNull FragmentManager fragmentManager) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
        }

        @NonNull
        @Override
        public Fragment getItem(@AppDetailsFragment.Property int position) {
            if (mTabFragments[position] == null) {
                switch (position) {
                    case AppDetailsFragment.APP_INFO:
                        mTabFragments[position] = new AppInfoFragment();
                        break;
                    case AppDetailsFragment.ACTIVITIES:
                    case AppDetailsFragment.SERVICES:
                    case AppDetailsFragment.RECEIVERS:
                    case AppDetailsFragment.PROVIDERS: {
                        mTabFragments[position] = new AppDetailsComponentsFragment();
                        Bundle args = new Bundle();
                        args.putInt(AppDetailsFragment.ARG_TYPE, position);
                        mTabFragments[position].setArguments(args);
                        break;
                    }
                    case AppDetailsFragment.APP_OPS:
                    case AppDetailsFragment.PERMISSIONS:
                    case AppDetailsFragment.USES_PERMISSIONS: {
                        mTabFragments[position] = new AppDetailsPermissionsFragment();
                        Bundle args = new Bundle();
                        args.putInt(AppDetailsFragment.ARG_TYPE, position);
                        mTabFragments[position].setArguments(args);
                        break;
                    }
                    case AppDetailsFragment.CONFIGURATIONS:
                    case AppDetailsFragment.FEATURES:
                    case AppDetailsFragment.SHARED_LIBRARIES:
                    case AppDetailsFragment.SIGNATURES: {
                        mTabFragments[position] = new AppDetailsOtherFragment();
                        Bundle args = new Bundle();
                        args.putInt(AppDetailsFragment.ARG_TYPE, position);
                        mTabFragments[position].setArguments(args);
                        break;
                    }
                }
            }
            return mTabFragments[position];
        }

        @Override
        public int getCount() {
            return mTabTitleIds.length();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return mTabTitleIds.getText(position);
        }
    }
}
