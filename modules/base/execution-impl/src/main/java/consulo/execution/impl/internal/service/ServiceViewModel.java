// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.execution.impl.internal.service;

import consulo.disposer.Disposable;
import consulo.execution.impl.internal.service.ServiceModel.ContributorNode;
import consulo.execution.impl.internal.service.ServiceModel.ServiceGroupNode;
import consulo.execution.impl.internal.service.ServiceModel.ServiceNode;
import consulo.execution.service.ServiceEventListener;
import consulo.execution.service.ServiceViewContributor;
import consulo.ui.ex.util.Invoker;
import consulo.ui.ex.util.InvokerSupplier;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ServiceViewModel implements Disposable, InvokerSupplier, ServiceModel.ServiceModelEventListener {
  protected final ServiceModel myModel;
  protected final ServiceModelFilter myModelFilter;
  private final ServiceModelFilter.ServiceViewFilter myFilter;
  private final List<ServiceViewModelListener> myListeners = new CopyOnWriteArrayList<>();
  private volatile boolean myShowGroups;
  private volatile boolean myShowContributorRoots;

  protected ServiceViewModel(@Nonnull ServiceModel model,
                             @Nonnull ServiceModelFilter modelFilter,
                             @Nonnull ServiceModelFilter.ServiceViewFilter filter) {
    myModel = model;
    myModelFilter = modelFilter;
    myFilter = filter;
    myModel.addEventListener(this);
  }

  @Nonnull
  List<? extends ServiceViewItem> getRoots() {
    return getRoots(false);
  }

  @Nonnull
  List<? extends ServiceViewItem> getVisibleRoots() {
    return getRoots(true);
  }

  @Nonnull
  private List<? extends ServiceViewItem> getRoots(boolean visible) {
    List<? extends ServiceViewItem> roots = processGroups(doGetRoots(), visible);
    if (roots.stream().anyMatch(ContributorNode.class::isInstance)) {
      if (myShowContributorRoots) {
        roots = ContainerUtil.filter(roots, item -> !(item instanceof ContributorNode) || !getChildren(item, visible).isEmpty());
      }
      else {
        roots = roots.stream()
                     .flatMap(item -> item instanceof ContributorNode ? getChildren(item, visible).stream() : Stream.of(item))
                     .collect(Collectors.toList());
      }
    }
    return roots;
  }

  @Nonnull
  protected abstract List<? extends ServiceViewItem> doGetRoots();

  void saveState(ServiceViewState viewState) {
    viewState.groupByServiceGroups = myShowGroups;
    viewState.groupByContributor = myShowContributorRoots;
  }

  void filtersChanged() {
    notifyListeners();
  }

  @Nonnull
  ServiceModelFilter.ServiceViewFilter getFilter() {
    return myFilter;
  }

  @Nonnull
  List<? extends ServiceViewItem> getChildren(@Nonnull ServiceViewItem parent) {
    return getChildren(parent, true);
  }

  @Nonnull
  protected List<? extends ServiceViewItem> getChildren(@Nonnull ServiceViewItem parent, boolean visible) {
    return processGroups(myModelFilter.filter(parent.getChildren(), myFilter), visible);
  }

  @Nullable
  protected ServiceViewItem findItemSafe(@Nonnull ServiceViewItem item) {
    ServiceViewItem updatedItem = findItem(item, myModel.getRoots());
    if (updatedItem != null) {
      return updatedItem;
    }
    return myModel.findItemSafe(item.getValue(), item.getRootContributor().getClass());
  }

  void addModelListener(@Nonnull ServiceViewModelListener listener) {
    myListeners.add(listener);
  }

  void removeModelListener(@Nonnull ServiceViewModelListener listener) {
    myListeners.remove(listener);
  }

  boolean isGroupByServiceGroups() {
    return myShowGroups;
  }

  void setGroupByServiceGroups(boolean value) {
    if (myShowGroups != value) {
      myShowGroups = value;
      notifyListeners();
    }
  }

  boolean isGroupByContributor() {
    return myShowContributorRoots;
  }

  void setGroupByContributor(boolean value) {
    if (myShowContributorRoots != value) {
      myShowContributorRoots = value;
      notifyListeners();
    }
  }

  protected void notifyListeners(ServiceEventListener.ServiceEvent e) {
    for (ServiceViewModelListener listener : myListeners) {
      listener.eventProcessed(e);
    }
  }

  protected void notifyListeners() {
    for (ServiceViewModelListener listener : myListeners) {
      listener.structureChanged();
    }
  }

  @Override
  public void dispose() {
    myModel.removeEventListener(this);
  }

  @Nonnull
  @Override
  public Invoker getInvoker() {
    return myModel.getInvoker();
  }

  @Nonnull
  private List<? extends ServiceViewItem> processGroups(@Nonnull List<? extends ServiceViewItem> items, boolean visible) {
    if (visible) {
      items = ContainerUtil.filter(items, item -> item.getViewDescriptor().isVisible());
    }
    if (myShowGroups) {
      return filterEmptyGroups(items, visible);
    }
    return items.stream()
                .flatMap(item -> item instanceof ServiceGroupNode ? getChildren(item, visible).stream() : Stream.of(item))
                .collect(Collectors.toList());
  }

  @Nonnull
  private List<? extends ServiceViewItem> filterEmptyGroups(@Nonnull List<? extends ServiceViewItem> items, boolean visible) {
    return ContainerUtil.filter(items, item -> !(item instanceof ServiceGroupNode) ||
      !filterEmptyGroups(getChildren(item, visible), visible).isEmpty());
  }

  static ServiceViewModel createModel(@Nonnull List<ServiceViewItem> items,
                                      @Nullable ServiceViewContributor<?> contributor,
                                      @Nonnull ServiceModel model,
                                      @Nonnull ServiceModelFilter modelFilter,
                                      @Nullable ServiceModelFilter.ServiceViewFilter parentFilter) {
    if (contributor != null && items.size() > 1) {
      ServiceViewItem contributorRoot = null;
      for (ServiceViewItem root : model.getRoots()) {
        if (contributor == root.getContributor()) {
          contributorRoot = root;
          break;
        }
      }
      if (contributorRoot != null && contributorRoot.getChildren().equals(items)) {
        return new ContributorModel(model, modelFilter, contributor, parentFilter);
      }
    }

    if (items.size() == 1) {
      ServiceViewItem item = items.get(0);
      if (item instanceof ContributorNode) {
        return new ContributorModel(model, modelFilter, item.getContributor(), parentFilter);
      }
      if (item instanceof ServiceGroupNode) {
        AtomicReference<ServiceGroupNode> ref = new AtomicReference<>((ServiceGroupNode)item);
        return new GroupModel(model, modelFilter, ref, parentFilter);
      }
      else if (isSingleService(item)) {
        AtomicReference<ServiceViewItem> ref = new AtomicReference<>(item);
        return new SingeServiceModel(model, modelFilter, ref, parentFilter);
      }
    }
    return new ServiceListModel(model, modelFilter, items, parentFilter);
  }

  private static boolean isSingleService(ServiceViewItem item) {
    if (item instanceof ServiceNode node) {
      if (!node.isChildrenInitialized() || !node.isLoaded()) {
        return false;
      }
    }
    return item.getChildren().isEmpty();
  }

  @Nullable
  static ServiceViewModel loadModel(@Nonnull ServiceViewState viewState,
                                    @Nonnull ServiceModel model,
                                    @Nonnull ServiceModelFilter modelFilter,
                                    @Nullable ServiceModelFilter.ServiceViewFilter parentFilter,
                                    @Nonnull Map<String, ServiceViewContributor<?>> contributors) {
    switch (viewState.viewType) {
      case ContributorModel.TYPE: {
        ServiceViewState.ServiceState serviceState = ContainerUtil.getOnlyItem(viewState.roots);
        ServiceViewContributor<?> contributor = serviceState == null ? null : contributors.get(serviceState.contributor);
        return contributor == null ? null : new ContributorModel(model, modelFilter, contributor, parentFilter);
      }
      case GroupModel.TYPE: {
        ServiceViewState.ServiceState serviceState = ContainerUtil.getOnlyItem(viewState.roots);
        ServiceViewContributor<?> contributor = serviceState == null ? null : contributors.get(serviceState.contributor);
        if (contributor == null) return null;

        ServiceViewItem groupItem = model.findItemById(serviceState.path, contributor);
        if (!(groupItem instanceof ServiceGroupNode)) return null;
        AtomicReference<ServiceGroupNode> ref = new AtomicReference<>((ServiceGroupNode)groupItem);
        return new GroupModel(model, modelFilter, ref, parentFilter);
      }
      case SingeServiceModel.TYPE: {
        ServiceViewState.ServiceState serviceState = ContainerUtil.getOnlyItem(viewState.roots);
        ServiceViewContributor<?> contributor = serviceState == null ? null : contributors.get(serviceState.contributor);
        if (contributor == null) return null;

        ServiceViewItem serviceItem = model.findItemById(serviceState.path, contributor);
        if (serviceItem == null) return null;

        if (serviceItem.getChildren().isEmpty()) {
          AtomicReference<ServiceViewItem> ref = new AtomicReference<>(serviceItem);
          return new SingeServiceModel(model, modelFilter, ref, parentFilter);
        }
        else {
          new ServiceListModel(model, modelFilter, new SmartList<>(serviceItem), parentFilter);
        }
      }
      case ServiceListModel.TYPE:
        List<ServiceViewItem> items = new ArrayList<>();
        for (ServiceViewState.ServiceState serviceState : viewState.roots) {
          ServiceViewContributor<?> contributor = contributors.get(serviceState.contributor);
          if (contributor != null) {
            ContainerUtil.addIfNotNull(items, model.findItemById(serviceState.path, contributor));
          }
        }
        return items.isEmpty() ? null : new ServiceListModel(model, modelFilter, items, parentFilter);
      default:
        return null;
    }
  }

  @Nullable
  protected static ServiceViewItem findItem(ServiceViewItem viewItem, List<? extends ServiceViewItem> modelItems) {
    return findItem(getPath(viewItem), modelItems);
  }

  @Nullable
  private static ServiceViewItem findItem(Deque<ServiceViewItem> path, List<? extends ServiceViewItem> modelItems) {
    ServiceViewItem node = path.removeFirst();
    for (ServiceViewItem root : modelItems) {
      if (root.equals(node)) {
        if (path.isEmpty()) {
          return root;
        }
        else {
          return findItem(path, root.getChildren());
        }
      }
    }
    return null;
  }

  protected static Deque<ServiceViewItem> getPath(ServiceViewItem item) {
    Deque<ServiceViewItem> path = new LinkedList<>();
    do {
      path.addFirst(item);
      item = item.getParent();
    }
    while (item != null);
    return path;
  }

  @Nullable
  private static List<String> getIdPath(@Nullable ServiceViewItem item) {
    List<String> path = new ArrayList<>();
    while (item != null) {
      String id = item.getViewDescriptor().getId();
      if (id == null) {
        return null;
      }
      path.add(id);
      item = item.getParent();
    }
    Collections.reverse(path);
    return path;
  }

  @Nullable
  private static ServiceViewState.ServiceState getState(@Nullable ServiceViewItem item) {
    if (item == null) return null;

    List<String> path = getIdPath(item);
    if (path == null) return null;

    ServiceViewState.ServiceState serviceState = new ServiceViewState.ServiceState();
    serviceState.contributor = item.getRootContributor().getClass().getName();
    serviceState.path = path;
    return serviceState;
  }

  interface ServiceViewModelListener {
    default void eventProcessed(@Nonnull ServiceEventListener.ServiceEvent e) {
      structureChanged();
    }

    void structureChanged();
  }

  static final class AllServicesModel extends ServiceViewModel {
    AllServicesModel(@Nonnull ServiceModel model, @Nonnull ServiceModelFilter modelFilter,
                     @Nonnull Collection<ServiceViewContributor<?>> contributors) {
      super(model, modelFilter, new ServiceModelFilter.ServiceViewFilter(null) {
        @Override
        public boolean test(ServiceViewItem item) {
          return contributors.contains(item.getRootContributor());
        }
      });
    }

    @Override
    @Nonnull
    protected List<? extends ServiceViewItem> doGetRoots() {
      return myModelFilter.filter(ContainerUtil.filter(myModel.getRoots(), getFilter()), getFilter());
    }

    @Override
    public void eventProcessed(ServiceEventListener.ServiceEvent e) {
      notifyListeners(e);
    }
  }

  static final class ContributorModel extends ServiceViewModel {
    private static final String TYPE = "contributor";

    private final ServiceViewContributor<?> myContributor;

    ContributorModel(@Nonnull ServiceModel model, @Nonnull ServiceModelFilter modelFilter, @Nonnull ServiceViewContributor<?> contributor,
                     @Nullable ServiceModelFilter.ServiceViewFilter parentFilter) {
      super(model, modelFilter, new ServiceModelFilter.ServiceViewFilter(parentFilter) {
        @Override
        public boolean test(ServiceViewItem item) {
          return contributor.equals(item.getContributor());
        }
      });
      myContributor = contributor;
    }

    @Nonnull
    @Override
    protected List<? extends ServiceViewItem> doGetRoots() {
      return myModelFilter.filter(ContainerUtil.filter(myModel.getRoots(), getFilter()), getFilter());
    }

    @Override
    public void eventProcessed(ServiceEventListener.ServiceEvent e) {
      if (e.contributorClass.isInstance(myContributor)) {
        notifyListeners(e);
      }
    }

    @Override
    void saveState(ServiceViewState viewState) {
      super.saveState(viewState);
      viewState.viewType = TYPE;
      ServiceViewState.ServiceState serviceState = new ServiceViewState.ServiceState();
      serviceState.contributor = myContributor.getClass().getName();
      viewState.roots = new SmartList<>(serviceState);
    }

    ServiceViewContributor<?> getContributor() {
      return myContributor;
    }
  }

  static final class GroupModel extends ServiceViewModel {
    private static final String TYPE = "group";

    private final AtomicReference<ServiceGroupNode> myGroupRef;

    GroupModel(@Nonnull ServiceModel model, @Nonnull ServiceModelFilter modelFilter,
               @Nonnull AtomicReference<ServiceGroupNode> groupRef, @Nullable ServiceModelFilter.ServiceViewFilter parentFilter) {
      super(model, modelFilter, new ServiceModelFilter.ServiceViewFilter(parentFilter) {
        @Override
        public boolean test(ServiceViewItem item) {
          ServiceGroupNode group = groupRef.get();
          ServiceViewItem parent = item.getParent();
          return parent != null && group != null && getPath(parent).equals(getPath(group));
        }
      });
      myGroupRef = groupRef;
    }

    @Nonnull
    @Override
    protected List<? extends ServiceViewItem> doGetRoots() {
      ServiceGroupNode group = myGroupRef.get();
      return group == null ? Collections.emptyList() : getChildren(group, false);
    }

    @Override
    public void eventProcessed(ServiceEventListener.ServiceEvent e) {
      ServiceGroupNode group = myGroupRef.get();
      if (group == null || !e.contributorClass.isInstance(group.getRootContributor())) return;

      myGroupRef.set((ServiceGroupNode)findItem(group, myModel.getRoots()));
      notifyListeners(e);
    }

    @Override
    void saveState(ServiceViewState viewState) {
      super.saveState(viewState);
      viewState.viewType = TYPE;
      ContainerUtil.addIfNotNull(viewState.roots, ServiceViewModel.getState(myGroupRef.get()));
    }

    ServiceGroupNode getGroup() {
      return myGroupRef.get();
    }
  }

  public static final class SingeServiceModel extends ServiceViewModel {
    private static final String TYPE = "service";

    private final AtomicReference<ServiceViewItem> myServiceRef;

    SingeServiceModel(@Nonnull ServiceModel model, @Nonnull ServiceModelFilter modelFilter,
                      @Nonnull AtomicReference<ServiceViewItem> serviceRef, @Nullable ServiceModelFilter.ServiceViewFilter parentFilter) {
      super(model, modelFilter, new ServiceModelFilter.ServiceViewFilter(parentFilter) {
        @Override
        public boolean test(ServiceViewItem item) {
          return item.equals(serviceRef.get());
        }
      });
      myServiceRef = serviceRef;
    }

    @Nonnull
    @Override
    protected List<? extends ServiceViewItem> doGetRoots() {
      ServiceViewItem service = myServiceRef.get();
      return ContainerUtil.createMaybeSingletonList(service);
    }

    @Override
    public void eventProcessed(ServiceEventListener.ServiceEvent e) {
      ServiceViewItem service = myServiceRef.get();
      if (service == null || !e.contributorClass.isInstance(service.getRootContributor())) return;

      myServiceRef.set(findItemSafe(service));
      notifyListeners(e);
    }

    @Override
    void saveState(ServiceViewState viewState) {
      super.saveState(viewState);
      viewState.viewType = TYPE;
      ContainerUtil.addIfNotNull(viewState.roots, ServiceViewModel.getState(myServiceRef.get()));
    }

    ServiceViewItem getService() {
      return myServiceRef.get();
    }
  }

  static final class ServiceListModel extends ServiceViewModel {
    private static final String TYPE = "services";

    private final List<ServiceViewItem> myRoots;

    ServiceListModel(@Nonnull ServiceModel model, @Nonnull ServiceModelFilter modelFilter, @Nonnull List<ServiceViewItem> roots,
                     @Nullable ServiceModelFilter.ServiceViewFilter parentFilter) {
      super(model, modelFilter, new ServiceModelFilter.ServiceViewFilter(parentFilter) {
        @Override
        public boolean test(ServiceViewItem item) {
          return roots.contains(item);
        }
      });
      myRoots = new CopyOnWriteArrayList<>(roots);
    }

    @Nonnull
    @Override
    protected List<? extends ServiceViewItem> doGetRoots() {
      return myModelFilter.filter(myRoots, getFilter());
    }

    @Override
    public void eventProcessed(ServiceEventListener.ServiceEvent e) {
      boolean update = false;

      List<ServiceViewItem> toRemove = new ArrayList<>();
      for (int i = 0; i < myRoots.size(); i++) {
        ServiceViewItem node = myRoots.get(i);
        if (!e.contributorClass.isInstance(node.getRootContributor())) continue;

        ServiceViewItem updatedNode = findItemSafe(node);
        if (updatedNode != null) {
          //noinspection SuspiciousListRemoveInLoop
          myRoots.remove(i);
          myRoots.add(i, updatedNode);
        }
        else {
          toRemove.add(node);
        }
        update = true;
      }
      myRoots.removeAll(toRemove);

      if (update) {
        notifyListeners(e);
      }
    }

    @Override
    void saveState(ServiceViewState viewState) {
      super.saveState(viewState);
      viewState.viewType = TYPE;
      for (ServiceViewItem root : myRoots) {
        ContainerUtil.addIfNotNull(viewState.roots, ServiceViewModel.getState(root));
      }
    }

    List<ServiceViewItem> getItems() {
      return myRoots;
    }
  }
}
