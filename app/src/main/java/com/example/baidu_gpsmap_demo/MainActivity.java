package com.example.baidu_gpsmap_demo;

import android.content.Context;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.mapapi.SDKInitializer;
import com.baidu.mapapi.map.BaiduMap;
import com.baidu.mapapi.map.BitmapDescriptor;
import com.baidu.mapapi.map.BitmapDescriptorFactory;
import com.baidu.mapapi.map.MapStatusUpdate;
import com.baidu.mapapi.map.MapStatusUpdateFactory;
import com.baidu.mapapi.map.MapView;
import com.baidu.mapapi.map.Marker;
import com.baidu.mapapi.map.MyLocationConfiguration;
import com.baidu.mapapi.map.MyLocationData;
import com.baidu.mapapi.model.LatLng;
import com.baidu.mapapi.overlayutil.WalkingRouteOverlay;
import com.baidu.mapapi.search.core.SearchResult;
import com.baidu.mapapi.search.route.BikingRouteResult;
import com.baidu.mapapi.search.route.DrivingRouteResult;
import com.baidu.mapapi.search.route.IndoorRouteResult;
import com.baidu.mapapi.search.route.MassTransitRouteResult;
import com.baidu.mapapi.search.route.OnGetRoutePlanResultListener;
import com.baidu.mapapi.search.route.PlanNode;
import com.baidu.mapapi.search.route.RoutePlanSearch;
import com.baidu.mapapi.search.route.TransitRouteResult;
import com.baidu.mapapi.search.route.WalkingRoutePlanOption;
import com.baidu.mapapi.search.route.WalkingRouteResult;

/**
 * @author Administrator
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    /**日志的TAG */
    private static final String TAG;
    static {
        TAG = "MainActivity";
    }

    /** 显示位置信息的TextView */
    private TextView positionText = null;
    /** 显示地图的控件 */
    private MapView mMapView;
    /** 地图管理器 */
    private BaiduMap mBaiduMap = null;
    /** 覆盖物 */
    private Marker marker = null;
    private Context context;
    /** 定位相关 经纬度信息 */
    private double mLatitude;
    private double mLongtitude;
    /** 方向传感器 */
    private MyOrientationListener mMyOrientationListener;
    private float mCurrentX;
    /** 自定义图标 初始化bitmap信息，不用的时候请及时回收recycle  覆盖物图标 */
    private BitmapDescriptor mIconLocation = BitmapDescriptorFactory.fromResource(R.drawable.location_arrows);
    /** 定位服务的客户端。宿主程序在客户端声明此类，并调用，目前只支持在主线程中启动 */
    private LocationClient mLocationClient;
    /** 是否是首次定位 */
    private boolean isFirstLoc  = true;
    public BDAbstractLocationListener myListener;
    private LatLng mLastLocationData;
    private View myView = null;
    /** 路线规划相关 */
    private RoutePlanSearch mSearch = null;
    private NetWorkStateReceiver netWorkStateReceiver;

    public MainActivity() {
        mMapView = null;
    }

    /** 定位 */
    private class MyLocationListener extends BDAbstractLocationListener {
        @Override
        public void onReceiveLocation(BDLocation location) {
            // Log.d(TAG, "BDLocationListener -> onReceiveLocation");
            /** 定位结果 */
            String addr;
            /** mapView 销毁后不在处理新接收的位置 */
            if (location == null || mMapView == null) {
                Log.d(TAG, "BDLocation or mapView is null");
                positionText.setText("定位失败...");
                return;
            }
            if(!location.getLocationDescribe().isEmpty()) {
                addr = location.getLocationDescribe();
            }else if (location.hasAddr()) {
                addr = location.getAddrStr();
            }else {
                Log.d(TAG, "BDLocation has no addr info");
                addr = "定位失败...";
                return;
            }
            MyLocationData locData = new MyLocationData.Builder()
                    .accuracy(location.getRadius())
                    // 此处设置开发者获取到的方向信息，顺时针0-360
                    .direction(mCurrentX).latitude(location.getLatitude())
                    .longitude(location.getLongitude()).build();
            mBaiduMap.setMyLocationData(locData);
            // 设置自定义图标
            MyLocationConfiguration config = new
                    MyLocationConfiguration(
                    MyLocationConfiguration.LocationMode.NORMAL, true, mIconLocation);
            mBaiduMap.setMyLocationConfiguration(config);
            // 更新经纬度
            mLatitude = location.getLatitude();
            mLongtitude = location.getLongitude();
            //设置起点
            mLastLocationData = new LatLng(mLatitude, mLongtitude);
            if (isFirstLoc ) {
                centerToMyLocation(location.getLatitude(), location.getLongitude());

                if (location.getLocType() == BDLocation.TypeGpsLocation) {
                    // GPS定位结果
                    Toast.makeText(context, "定位:" + location.getAddrStr(), Toast.LENGTH_SHORT).show();
                } else if (location.getLocType() == BDLocation.TypeNetWorkLocation) {
                    // 网络定位结果
                    Toast.makeText(context, "定位:" + location.getAddrStr(), Toast.LENGTH_SHORT).show();
                } else if (location.getLocType() == BDLocation.TypeOffLineLocation) {
                    // 离线定位结果
                    Toast.makeText(context, "定位:" + location.getAddrStr(), Toast.LENGTH_SHORT).show();
                } else if (location.getLocType() == BDLocation.TypeServerError) {
                    Toast.makeText(context, "定位:服务器错误", Toast.LENGTH_SHORT).show();
                } else if (location.getLocType() == BDLocation.TypeNetWorkException) {
                    Toast.makeText(context, "定位:网络错误", Toast.LENGTH_SHORT).show();
                } else if (location.getLocType() == BDLocation.TypeCriteriaException) {
                    Toast.makeText(context, "定位:手机模式错误，请检查是否飞行", Toast.LENGTH_SHORT).show();
                }
                isFirstLoc  = false;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.context = this;
        positionText = findViewById(R.id.showLoc);
        myView = findViewById(R.id.view_attribute);
        mMapView = findViewById(R.id.b_map_View);
        //获取地图控件引用
        mBaiduMap = mMapView.getMap();
        initMyLocation();
        initPoutePlan();
        button();
    }

    @Override
    protected void onStart() {
        super.onStart();
        //开启定位
        mBaiduMap.setMyLocationEnabled(true);
        if (!mLocationClient.isStarted()) {
            mLocationClient.start();
        }
        //开启方向传感器
        mMyOrientationListener.start();
    }

    @Override
    protected void onResume() {
        if (netWorkStateReceiver == null) {
            netWorkStateReceiver = new NetWorkStateReceiver();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(netWorkStateReceiver, filter);
        System.out.println("注册");
        super.onResume();
        mMapView.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(netWorkStateReceiver);
        System.out.println("注销");
        super.onPause();
        mMapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        //停止定位
        mBaiduMap.setMyLocationEnabled(false);
        mLocationClient.stop();
        //停止方向传感器
        mMyOrientationListener.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBaiduMap.setMyLocationEnabled(false);
        mMapView.onDestroy();
        mMapView = null;
        mSearch.destroy();
    }

    //按钮响应
    private void button() {
        //按钮
        Button mbut_Loc = findViewById(R.id.but_Loc);
        Button mbut_RoutrPlan = findViewById(R.id.but_RoutrPlan);
        Button mbut_Attribute = findViewById(R.id.but_Attribute);
        Button mbut_Command = findViewById(R.id.but_Command);
        //按钮处理
        mbut_Loc.setOnClickListener(this);
        mbut_RoutrPlan.setOnClickListener(this);
        mbut_Attribute.setOnClickListener(this);
        mbut_Command.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        SDKInitializer.initialize(this.getApplicationContext());
        switch (v.getId()) {
            case R.id.but_Loc: {
                centerToMyLocation(mLatitude, mLongtitude);
                break;
            }
            case R.id.but_RoutrPlan: {
                StarRoute();
                break;
            }
            case R.id.but_Attribute: {
                ShowViewAttribute(v);
                break;
            }
            case R.id.but_Command: {
                ShowViewCommand(v);
                break;
            }
        }
    }

    /** 初始化定位 */
    private void initMyLocation() {
        // 地图初始化
        mBaiduMap = mMapView.getMap();
        //缩放地图
        MapStatusUpdate msu = MapStatusUpdateFactory.zoomTo(15.0f);
        mBaiduMap.setMapStatus(msu);
        //开启定位图层
        mBaiduMap.setMyLocationEnabled(true);
        //声明LocationClient类
        mLocationClient = new LocationClient(this);
        //通过LocationClientOption设置LocationClient相关参数
        LocationClientOption option = new LocationClientOption();
        // 设置坐标类型
        option.setCoorType("bd09ll");
        int span = 3000;
        option.setScanSpan(span);
        // 设置是否需要地址信息，默认为无地址
        option.setIsNeedAddress(true);
        option.setIsNeedLocationDescribe(true);
        option.setIsNeedLocationPoiList(true);
        option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
        option.setOpenGps(true);
        //设置locationClientOption
        mLocationClient.setLocOption(option);
        myListener = new MyLocationListener();
        //初始化图标
        mIconLocation = BitmapDescriptorFactory.fromResource(R.drawable.navi_map_gps);
        //注册监听函数
        mLocationClient.registerLocationListener(myListener);
        //初始化传感器
        initOrientation();
        //开始定位，定位结果会回调到前面注册的监听器中
        mLocationClient.start();
    }

    //传感器
    private void initOrientation() {
        //传感器
        mMyOrientationListener = new MyOrientationListener(context);
        mMyOrientationListener.setOnOrientationListener(new MyOrientationListener.OnOrientationListener() {
            @Override
            public void onOrientationChanged(float x) {
                mCurrentX = x;
            }
        });
    }

    //路线规划初始化
    private void initPoutePlan() {
        mSearch = RoutePlanSearch.newInstance();
        mSearch.setOnGetRoutePlanResultListener(listener);
    }

    // 路线规划模块
    public OnGetRoutePlanResultListener listener = new OnGetRoutePlanResultListener() {
        @Override
        public void onGetWalkingRouteResult(WalkingRouteResult result) {
            if (result == null || result.error != SearchResult.ERRORNO.NO_ERROR) {
                Toast.makeText(MainActivity.this, "路线规划:未找到结果,检查输入", Toast.LENGTH_SHORT).show();
                //禁止定位
                isFirstLoc  = false;
            }
            assert result != null;
            if (result.error == SearchResult.ERRORNO.AMBIGUOUS_ROURE_ADDR) {
                // 起终点或途经点地址有岐义，通过以下接口获取建议查询信息
                result.getSuggestAddrInfo();
                return;
            }
            if (result.error == SearchResult.ERRORNO.NO_ERROR) {
                mBaiduMap.clear();
                Toast.makeText(MainActivity.this, "路线规划:搜索完成", Toast.LENGTH_SHORT).show();
                WalkingRouteOverlay overlay = new WalkingRouteOverlay(mBaiduMap);
                overlay.setData(result.getRouteLines().get(0));
                overlay.addToMap();
                overlay.zoomToSpan();
            }
            //禁止定位
            isFirstLoc  = false;
        }

        @Override
        public void onGetTransitRouteResult(TransitRouteResult var1) {
        }

        @Override
        public void onGetMassTransitRouteResult(MassTransitRouteResult var1) {
        }

        @Override
        public void onGetDrivingRouteResult(DrivingRouteResult result) {
        }

        @Override
        public void onGetIndoorRouteResult(IndoorRouteResult var1) {
        }

        @Override
        public void onGetBikingRouteResult(BikingRouteResult var1) {
        }
    };

    //回到定位中心
    private void centerToMyLocation(double latitude, double longtitude) {
        mBaiduMap.clear();
        mLastLocationData = new LatLng(latitude, longtitude);
        MapStatusUpdate msu = MapStatusUpdateFactory.newLatLng(mLastLocationData);
        mBaiduMap.animateMapStatus(msu);
    }
    //开始规划
    private void StarRoute() {
        SDKInitializer.initialize(getApplicationContext());
        // 设置起、终点信息
        PlanNode stNode = PlanNode.withCityNameAndPlaceName("北京", "西二旗地铁站");
        PlanNode enNode = PlanNode.withCityNameAndPlaceName("北京", "百度科技园");
        mSearch.walkingSearch((new WalkingRoutePlanOption())
                .from(stNode)
                .to(enNode));
    }
    // 属性View显示
    private void ShowViewAttribute(View v) {
        Toast.makeText(getApplicationContext(), "按钮被点击了", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "ShowViewAttribute+进入函数");
        if(myView.getVisibility() == View.VISIBLE){
            //myView.setVisibility(View.INVISIBLE);
            Log.d(TAG, "ShowViewAttribute+LEFT" + myView.getLeft());
            myView.setVisibility(View.INVISIBLE);
            Log.d(TAG, "ShowViewAttribute+函数内部");
        }else {
            myView.setVisibility(View.VISIBLE);
            Log.d(TAG, "ShowViewAttribute+函数内部2");
        }
    }
    // 命令View显示
    private void ShowViewCommand(View v) {
        Log.d(TAG, "ShowViewCommand+进入函数");
    }

    //无任何效果的弹窗
   /* private void showNoneEffect() {
        LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View vPopupWindow = inflater.inflate(R.layout.popupwindow, null, false);//引入弹窗布局
        popupWindow = new PopupWindow(vPopupWindow, ActionBar.LayoutParams.MATCH_PARENT, ActionBar.LayoutParams.WRAP_CONTENT, true);
        //引入依附的布局
        View parentView = LayoutInflater.from(PopupWindowActivity.this).inflate(R.layout.layout_popupwindow, null);
        //相对于父控件的位置（例如正中央Gravity.CENTER，下方Gravity.BOTTOM等），可以设置偏移或无偏移
        popupWindow.showAtLocation(parentView, Gravity.BOTTOM, 0, 0);
    }*/
}


