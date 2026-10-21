const $ = (selector) => document.querySelector(selector);
const peso = (amount) => '₱' + Number(amount || 0).toLocaleString('en-PH', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const GUEST_CART_KEY = 'minimarket_guest_cart';
let products = [], cart = [], guestCart = [], account = null, category = 'All', query = '', authMode = 'customer', registerMode = false, postAuthView = 'shop';
const icons = { 'soap':'🧼','shampoo':'🧴','toothpaste':'🪥','oil':'🫒','milk':'🥛','detergent':'🫧','noodles':'🍜','tea':'🍵','sugar':'🧂','coffee':'☕','tissues':'🧻','rice':'🍚','sardines':'🐟','salt':'🧂','sauce':'🍶','toothbrush':'🪥','hairbrush':'💇','egg':'🥚' };
const esc = (value) => String(value).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const categoryFor = name => /soap|shampoo|toothpaste|toothbrush|hairbrush/i.test(name) ? 'Personal care' : /detergent|tissues/i.test(name) ? 'Household' : 'Pantry';
const iconFor = name => icons[Object.keys(icons).find(key => name.toLowerCase().includes(key))] || '🛒';
async function api(path, values) {
  const options = { method: values ? 'POST' : 'GET', credentials: 'same-origin', headers: {} };
  if (values) { options.headers['Content-Type'] = 'application/x-www-form-urlencoded;charset=UTF-8'; options.body = new URLSearchParams(values); }
  const response = await fetch('/api' + path, options);
  const data = await response.json();
  if (!response.ok) throw new Error(data.message || 'Request failed.');
  return data;
}
function readGuestCart() {
  const saved = localStorage.getItem(GUEST_CART_KEY);
  if (!saved) return [];

  let items;
  try { items = JSON.parse(saved); }
  catch (_) { throw new Error('The saved guest cart could not be read. Its data was left untouched.'); }
  if (!Array.isArray(items)) throw new Error('The saved guest cart is invalid. Its data was left untouched.');

  const seen = new Set();
  return items.map(item => {
    const productId = Number(item?.productId), quantity = Number(item?.quantity), price = Number(item?.price);
    if (!Number.isSafeInteger(productId) || productId < 1 || !Number.isInteger(quantity) || quantity < 1 || quantity > 999
        || typeof item.name !== 'string' || !Number.isFinite(price) || price < 0 || seen.has(productId)) {
      throw new Error('The saved guest cart is invalid. Its data was left untouched.');
    }
    seen.add(productId);
    return { productId, name: item.name, price, quantity };
  });
}
function saveGuestCart(items) {
  localStorage.setItem(GUEST_CART_KEY, JSON.stringify(items));
}
function guestCartLines() {
  return guestCart.map(item => {
    const product = products.find(entry => entry.id === item.productId);
    const name = product ? product.name : item.name;
    const price = product ? Number(product.price) : item.price;
    return { productId: item.productId, name, quantity: item.quantity, lineTotal: price * item.quantity, isGuest: true };
  });
}
function updateCartNavigation() {
  const isCustomer = account?.role === 'customer';
  const isAdmin = account?.role === 'admin';
  document.querySelector('.nav [data-view="cart"]').hidden = isAdmin;
  $('#nav-dashboard').hidden = !isAdmin;
  $('#account-button').textContent = account ? 'Log out' : 'Sign in';
  $('#account-button').title = account ? `Sign out ${account.username}` : 'Sign in';
  $('#balance-pill').hidden = !isCustomer;
  $('#admin-auth-form').hidden = isAdmin;
  $('#admin-registration').hidden = !isAdmin;
  $('#admin-auth-back').hidden = isAdmin;
  $('#admin-auth-dashboard').hidden = !isAdmin;
}
function toast(message) { const element = $('#toast'); element.textContent = message; element.classList.add('show'); clearTimeout(toast.timer); toast.timer = setTimeout(() => element.classList.remove('show'), 2600); }
function setBalance(value) { $('#balance').textContent = account?.role === 'customer' ? peso(value) : ''; }
function authScreen(role = 'customer', register = false) {
  authMode = role;
  registerMode = register;
  if (role === 'admin') {
    $('#admin-registration').hidden = account?.role !== 'admin';
    $('#admin-auth-form').hidden = account?.role === 'admin';
    $('#admin-auth-back').hidden = account?.role === 'admin';
    $('#admin-auth-dashboard').hidden = account?.role !== 'admin';
    showView('admin-auth');
    return;
  }
  $('#signin-role-switch').hidden = register;
  $('#customer-signin').classList.toggle('selected', true);
  $('#admin-signin').classList.toggle('selected', false);
  $('#customer-signin').setAttribute('aria-pressed', 'true');
  $('#admin-signin').setAttribute('aria-pressed', 'false');
  $('#auth-mode').textContent = register ? 'Create account' : 'Customer account';
  $('#auth-title').textContent = register ? 'Create your account' : 'Sign in to your account';
  $('#auth-submit').textContent = register ? 'Create account' : 'Sign in';
  $('#toggle-copy').textContent = register ? 'Already have an account?' : 'New here?';
  $('#toggle-auth').textContent = register ? 'Sign in' : 'Create an account';
  $('#toggle-auth').hidden = false;
  $('#password').autocomplete = register ? 'new-password' : 'current-password';
  showView('auth');
}
async function loadProducts() {
  products = await api('/products');
  products = products.map(product => ({...product, category: categoryFor(product.name), emoji: iconFor(product.name), detail: product.name}));
  renderProducts();
}
function renderProducts() {
  const list = products.filter(p => (category === 'All' || p.category === category) && p.name.toLowerCase().includes(query.toLowerCase()));
  $('#products').innerHTML = list.map(p => `<article class="product"><div class="product-art"><span class="tag">${esc(p.category)}</span>${p.emoji}</div><h3>${esc(p.name)}</h3><p class="sub">${esc(p.detail)}</p><div class="product-foot"><span class="price">${peso(p.price)} <small>/ item</small></span>${p.quantity > 0 ? `<button class="add" aria-label="Add ${esc(p.name)} to cart" data-add="${p.id}">+</button>` : '<span class="sub">Out of stock</span>'}</div></article>`).join('') || '<div class="empty">No products found.</div>';
  document.querySelectorAll('[data-add]').forEach(button => button.onclick = () => add(+button.dataset.add));
}
function total() { return cart.reduce((sum, line) => sum + Number(line.lineTotal), 0); }
function renderCart() {
  const count = cart.reduce((sum, line) => sum + Number(line.quantity), 0);
  $('#nav-count').textContent = count;   $('#item-count').textContent = count;
  $('#cart-total').textContent = peso(total());
  $('#remaining').textContent = account?.role === 'customer' ? peso((account.balance || 0) - total()) : 'Sign in to view balance';
  $('#modal-total').textContent = peso(total());
  $('#checkout').textContent = account?.role === 'customer' ? 'Review order' : 'Sign in to check out';
  $('#cart-signin-note').hidden = account?.role === 'customer';
  const box = $('#cart-items');
  box.innerHTML = cart.length ? cart.map(line => {
    const product = products.find(item => item.id === line.productId);
    const name = product ? product.name : line.name;
    const unit = Number(line.lineTotal) / Number(line.quantity);
    const guest = line.isGuest ? 'true' : 'false';
    return `<div class="cart-row"><div class="cart-emoji">${iconFor(name)}</div><div class="cart-name">${esc(name)}${line.isGuest ? '<small>Saved on this device</small>' : ''}<small>${peso(unit)} each</small></div><div class="qty"><button aria-label="Decrease ${esc(name)}" data-quantity="${line.productId}" data-guest="${guest}" data-value="${line.quantity - 1}">−</button><span>${line.quantity}</span><button aria-label="Increase ${esc(name)}" data-quantity="${line.productId}" data-guest="${guest}" data-value="${line.quantity + 1}">+</button></div><strong class="line-price">${peso(line.lineTotal)}</strong></div>`;
  }).join('') : '<div class="empty"><div style="font-size:42px">🛒</div><b>Your cart is waiting</b><span>Add a few everyday essentials to get started.</span><br><button class="secondary" style="margin-top:17px" data-view="shop">Browse products</button></div>';
  document.querySelectorAll('[data-quantity]').forEach(button => button.onclick = async () => {
    try {
      if (button.dataset.guest === 'true') {
        const productId = Number(button.dataset.quantity), quantity = Number(button.dataset.value);
        if (quantity > 999) { toast('You can add up to 999 of each product.'); return; }
          const product = products.find(entry => entry.id === productId);
          if (quantity > (product?.quantity ?? 0)) { toast(`Only ${product?.quantity ?? 0} in stock.`); return; }
          const next = quantity < 1
          ? guestCart.filter(item => item.productId !== productId)
          : guestCart.map(item => item.productId === productId ? { ...item, quantity } : item);
        saveGuestCart(next);
        guestCart = next;
        cart = guestCartLines();
        renderCart();
      } else {
        cart = await api('/cart/quantity', { productId: button.dataset.quantity, quantity: button.dataset.value });
        renderCart();
      }
    }
    catch (error) { toast(error.message); }
  });
  document.querySelectorAll('[data-view]').forEach(bindView);
}
async function mergeGuestCart() {
  let merged = 0, errorMessage = '';
  for (const item of [...guestCart]) {
    if (!products.some(product => product.id === item.productId)) {
      errorMessage = errorMessage || `Saved product "${item.name}" is no longer available.`;
      continue;
    }
    try { await api('/cart/add', { productId: item.productId, quantity: item.quantity }); }
    catch (error) { errorMessage = errorMessage || error.message; continue; }
    const next = guestCart.filter(entry => entry.productId !== item.productId);
    saveGuestCart(next);
    guestCart = next;
    merged++;
  }
  return { merged, remaining: guestCart.length, errorMessage };
}
async function loadCart() {
  let mergeResult = { merged: 0, remaining: guestCart.length, errorMessage: '' };
  if (account?.role === 'customer') {
    if (guestCart.length && Number(account.balance) > 0) mergeResult = await mergeGuestCart();
    cart = await api('/cart');
    cart = cart.concat(guestCartLines());
  } else {
    cart = account?.role === 'admin' ? [] : guestCartLines();
  }
  renderCart();
  return mergeResult;
}
async function add(id) {
  if (!account) {
    const product = products.find(item => item.id === id);
    if (!product) { toast('That product is no longer available.'); return; }
    const existing = guestCart.find(item => item.productId === id);
    if (existing?.quantity >= product.quantity) { toast(`Only ${product.quantity} in stock.`); return; }
    if (existing?.quantity >= 999) { toast('You can add up to 999 of each product.'); return; }
    const next = existing
      ? guestCart.map(item => item.productId === id ? { ...item, quantity: item.quantity + 1 } : item)
      : guestCart.concat({ productId: id, name: product.name, price: Number(product.price), quantity: 1 });
    try {
      saveGuestCart(next);
      guestCart = next;
      cart = guestCartLines();
      renderCart();
      toast('Added to your saved guest cart.');
    } catch (error) { toast(`Could not save your guest cart: ${error.message}`); }
    return;
  }
  try { cart = await api('/cart/add', { productId: id, quantity: 1 }); renderCart(); toast('Added to your cart.'); }
  catch (error) { toast(error.message); }
}
function showView(name) {
  document.querySelectorAll('.view').forEach(view => view.classList.remove('active'));
  $(`#${name}-view`).classList.add('active');
  document.querySelectorAll('.nav [data-view]').forEach(button => button.classList.toggle('active', button.dataset.view === name));
  if (name === 'cart') loadCart().catch(error => toast(error.message));
  if (name === 'admin') loadAdmin().catch(error => { authScreen('admin'); toast(error.message); });
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
function bindView(button) {
  button.onclick = () => {
    if (button.dataset.view === 'admin' && account?.role !== 'admin') { showView('shop'); return; }
    if (button.dataset.view === 'admin-auth' && account?.role !== 'admin') { authScreen('admin'); return; }
    showView(button.dataset.view);
  };
}
async function updateAccount(data) {
  account = data;
  $('#shop-view .welcome h1').textContent = `Welcome, ${data.username}`;
  setBalance(data.balance);
  updateCartNavigation();
  return loadCart();
}
async function loadAdmin() {
  if (account?.role !== 'admin') throw new Error('Administrator sign in is required.');
  const customers = await api('/admin/customers');
  $('#admin-table-body').innerHTML = customers.map(customer => `<tr><td>${esc(customer.username)}</td><td>${peso(customer.balance)}</td></tr>`).join('') || '<tr><td colspan="2">No registered customers.</td></tr>';
  await loadProducts();
  renderAdminProducts();
}
function renderAdminProducts() {
  $('#admin-products-body').innerHTML = products.map(product => `<tr><td>${esc(product.name)}</td><td>${peso(product.price)}</td><td>${product.quantity}</td><td>${esc(product.category)}</td></tr>`).join('') || '<tr><td colspan="4">No products are available.</td></tr>';
}
async function completeSignIn(data) {
  let mergeResult = await updateAccount(data);
  let balanceNeedsSetup = false;
  if (data.role === 'customer' && Number(data.balance) === 0) {
    const initial = window.prompt('Set your starting demo balance in PHP:', '1000');
    if (initial === null || !Number.isFinite(Number(initial)) || Number(initial) < 0) {
      balanceNeedsSetup = true;
    } else {
      data = await api('/balance', { balance: initial });
      mergeResult = await updateAccount(data);
    }
  }

  const destination = data.role === 'admin' ? 'admin' : postAuthView;
  postAuthView = 'shop';
  showView(destination);
  let message = `Signed in as ${data.username}.`;
  if (mergeResult.remaining) {
    message += ` ${mergeResult.remaining} saved item(s) remain in your guest cart`;
    message += mergeResult.errorMessage ? `: ${mergeResult.errorMessage}` : '.';
    if (balanceNeedsSetup) message += ' Set a demo balance to move them into your account.';
  } else if (mergeResult.merged) {
    message += ' Your saved guest cart was added to your account.';
  } else if (balanceNeedsSetup) {
    message += ' Set a demo balance later to shop.';
  }
  toast(message);
}
document.querySelectorAll('[data-view]').forEach(bindView);
$('#admin-signin').onclick = () => authScreen('admin');
$('#customer-signin').onclick = () => {
  $('#customer-signin').classList.add('selected');
  $('#admin-signin').classList.remove('selected');
  $('#customer-signin').setAttribute('aria-pressed', 'true');
  $('#admin-signin').setAttribute('aria-pressed', 'false');
};
$('#categories').onclick = event => { if (!event.target.dataset.category) return; category = event.target.dataset.category; document.querySelectorAll('.chip').forEach(chip => chip.classList.toggle('selected', chip === event.target)); renderProducts(); };
$('#search').oninput = event => { query = event.target.value; renderProducts(); };
$('#product-form').onsubmit = async event => {
  event.preventDefault();
  if (account?.role !== 'admin') { toast('Administrator sign in is required to add products.'); return; }
  const form = event.currentTarget;
  const submit = $('#product-submit');
  submit.disabled = true;
  try {
    const created = await api('/admin/products', {
      name: $('#product-name').value.trim(),
      price: $('#product-price').value,
      quantity: $('#product-quantity').value
    });
    form.reset();
    toast(`${created.name} added to the product catalog.`);
    try {
      await loadProducts();
      renderAdminProducts();
    } catch (error) {
      toast(`Product added, but the catalog could not be refreshed: ${error.message}`);
    }
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
};
$('#admin-auth-form').onsubmit = async event => {
  event.preventDefault();
  const submit = $('#admin-auth-submit');
  submit.disabled = true;
  try {
    const data = await api('/admin/login', {
      username: $('#admin-username').value.trim(),
      password: $('#admin-password').value
    });
    await completeSignIn(data);
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
};
$('#admin-auth-back').onclick = () => authScreen('customer');
$('#admin-register-form').onsubmit = async event => {
  event.preventDefault();
  if (account?.role !== 'admin') { toast('Sign in as an administrator before registering another admin.'); return; }
  const submit = $('#admin-register-submit');
  submit.disabled = true;
  try {
    const result = await api('/admin/register', {
      currentPassword: $('#admin-current-password').value,
      username: $('#new-admin-username').value.trim(),
      password: $('#new-admin-password').value
    });
    event.currentTarget.reset();
    toast(result.message || 'Administrator account created.');
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
};
$('#account-button').onclick = async () => {
  if (!account) {
    postAuthView = $('.view.active').id === 'cart-view' ? 'cart' : 'shop';
    authScreen();
    $('#username').focus();
    return;
  }
  try {
    await api('/logout', {});
    account = null;
    cart = guestCartLines();
    $('#shop-view .welcome h1').textContent = 'Shop everyday essentials';
    setBalance(null);
    updateCartNavigation();
    renderCart();
    showView('shop');
    toast('You have signed out.');
  }
  catch (error) { toast(error.message); }
};
$('#toggle-auth').onclick = () => authScreen('customer', !registerMode);
$('#auth-form').onsubmit = async event => {
  event.preventDefault(); const fields = { username: $('#username').value.trim(), password: $('#password').value };
  const submit = $('#auth-submit');
  submit.disabled = true;
  try {
    if (authMode === 'customer' && $('#auth-submit').textContent === 'Create account') {
      await api('/register', fields);
      await completeSignIn(await api('/login', fields));
      return;
    }
    const data = await api(authMode === 'admin' ? '/admin/login' : '/login', fields);
    await completeSignIn(data);
  } catch (error) { toast(error.message); }
  finally { submit.disabled = false; }
};
$('#checkout').onclick = async () => {
  if (!total()) { toast('Your cart is empty.'); return; }
  if (!account) {
    postAuthView = 'cart';
    authScreen();
    $('#username').focus();
    toast('Sign in or create an account to check out. Your guest cart is saved on this device.');
    return;
  }
  try {
    const mergeResult = await loadCart();
    if (mergeResult.remaining) {
      toast('Set a sufficient demo balance and review your saved guest items before checkout.');
      return;
    }
    if (!total()) { toast('Your cart is empty.'); return; }
    const selected = $('input[name="cart-payment"]:checked');
    const labels = { CASH_ON_DELIVERY: 'Cash on Delivery', GCASH: 'GCash', CARD: 'Card' };
    $('#confirmation-payment').textContent = labels[selected.value];
    $('#confirm-checkout').dataset.paymentMethod = selected.value;
    $('#checkout-error').hidden = true;
    $('#checkout-modal').classList.add('open');
  } catch (error) { toast(error.message); }
};
$('#cancel-checkout').onclick = () => {
  $('#checkout-modal').classList.remove('open');
  $('#checkout-error').hidden = true;
};
$('#confirm-checkout').onclick = async () => {
  const button = $('#confirm-checkout');
  button.disabled = true;
  try {
    const receipt = await api('/checkout', { paymentMethod: button.dataset.paymentMethod });
    account.balance = receipt.balance;
    setBalance(receipt.balance);
    cart = [];
    $('#checkout-modal').classList.remove('open');
    renderCart();
    showView('shop');
    toast(`Demo order complete · ${peso(receipt.total)} charged to balance.`);
    loadProducts().catch(error => toast(`Order complete, but product stock could not refresh: ${error.message}`));
  } catch (error) {
    $('#checkout-error').textContent = error.message;
    $('#checkout-error').hidden = false;
  } finally { button.disabled = false; }
};
(async function init() {
  try {
    try { guestCart = readGuestCart(); }
    catch (error) { toast(error.message); }
    await loadProducts();
    cart = guestCartLines();
    renderCart();
    updateCartNavigation();
    try {
      const current = await api('/me');
      const mergeResult = await updateAccount(current);
      if (current.role === 'admin') showView('admin');
      else if (mergeResult.remaining) toast('Some saved guest items could not be added to your account yet.');
    } catch (error) {
      account = null;
      setBalance(null);
      updateCartNavigation();
      cart = guestCartLines();
      renderCart();
      if (!error.message.includes('Please sign in')) toast(error.message);
    }
  } catch (error) { $('#products').innerHTML = `<div class="empty">${esc(error.message)}<br>Make sure the Java web server is running.</div>`; }
})();
