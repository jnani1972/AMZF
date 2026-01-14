# UI Design Guidelines for Multi-Broker Trading Platform

## Overview

This document establishes UI design standards and best practices for creating a seamless, intuitive user experience in the multi-broker trading application. These guidelines balance clarity with functionality while supporting different user roles, devices, and brokers.

**Last Updated:** 2026-01-15

---

## 🧭 Core Design Principles

### 1. Clarity Over Simplicity

**Philosophy:** Trading apps must display complex data without overwhelming users.

**Implementation:**
- Use a clean layout with plenty of whitespace
- Highlight only critical elements: prices, buy/sell actions, charts, account status
- Tuck secondary data behind tabs or expandable sections
- Use progressive disclosure: show basics first, details on demand

**Example:**
```
✅ GOOD: Main View
┌─────────────────────────────────────┐
│ Portfolio: ₹2,45,000 (+2.3%) ↑      │
│                                     │
│ [Buy] [Sell]  RELIANCE  ₹2,450.50  │
│                                     │
│ ▼ Advanced Options                  │
└─────────────────────────────────────┘

❌ BAD: Main View
┌─────────────────────────────────────┐
│ Portfolio: ₹2,45,000                │
│ P&L: +5,230  Day P&L: +1,250        │
│ Open Orders: 3  Positions: 12       │
│ Margin Used: 45%  Available: 55%    │
│ RELIANCE ₹2,450.50 +12.30 +0.51%    │
│ Product: MIS  Qty: 10  GTT: No      │
│ Stop Loss: 2,400  Target: 2,500     │
└─────────────────────────────────────┘
```

---

### 2. Role-Specific Interfaces

**User Roles:**
1. **Casual Investors** - Buy & hold, monthly reviews
2. **Day Traders** - Multiple trades daily, technical analysis
3. **Admins** - System monitoring, user management
4. **Analysts** - Backtesting, performance reports

**Implementation:**
- Provide customizable dashboards per role
- Hide advanced features from novices (e.g., bracket orders, GTT)
- Use progressive feature unlocking based on experience
- Allow "switch to advanced mode" toggle

**Dashboard Presets:**

**Casual Investor View:**
```
┌──────────────────────────────┐
│ My Holdings                  │
│ ─────────────────────────    │
│ RELIANCE    ₹2,450  +2.3%    │
│ SBIN        ₹595    -0.5%    │
│ INFY        ₹1,520  +1.2%    │
│                              │
│ [+ Buy More]  [Sell]         │
└──────────────────────────────┘
```

**Day Trader View:**
```
┌─────────────┬─────────────┬─────────────┐
│ Watchlist   │ Chart       │ Order Book  │
│             │             │             │
│ RELIANCE    │ [Candlestick│ Buy  2450   │
│ SBIN        │  with       │ Sell 2451   │
│ INFY        │  indicators]│ Depth: 1000 │
│             │             │             │
│ [Quick Buy] │ [Indicators]│ [Place Ord] │
└─────────────┴─────────────┴─────────────┘
```

---

### 3. Mobile-First, Responsive Design

**Priority:** Design for small screens first, scale up to desktop.

**Mobile Design Rules:**
- **Touch Targets:** Minimum 44x44px tap areas
- **Single Column:** Stack elements vertically
- **Bottom Navigation:** Place primary actions within thumb reach
- **Swipe Gestures:** Use for tab switching, dismissing panels
- **Minimal Steps:** 1-tap buy, 2-tap sell with confirmation

**Responsive Breakpoints:**
```css
/* Mobile: 320px - 768px */
.order-panel { width: 100%; }
.chart { height: 200px; }
.nav { position: fixed; bottom: 0; }

/* Tablet: 769px - 1024px */
.order-panel { width: 50%; }
.chart { height: 400px; }
.nav { position: static; }

/* Desktop: 1025px+ */
.order-panel { width: 33%; }
.chart { height: 600px; }
.multi-monitor { display: flex; }
```

**Mobile Order Entry:**
```
Step 1: Select Stock
┌──────────────────┐
│ Search: RELI...  │
│ ──────────────── │
│ ✓ RELIANCE       │
│   ₹2,450.50      │
│                  │
│ [Next →]         │
└──────────────────┘

Step 2: Quantity
┌──────────────────┐
│ Quantity         │
│ ──────────────── │
│     [10]         │
│ [- 5]  [+ 10]    │
│                  │
│ Est: ₹24,505     │
│ [Next →]         │
└──────────────────┘

Step 3: Confirm
┌──────────────────┐
│ Buy RELIANCE     │
│ ──────────────── │
│ Qty: 10          │
│ Price: ₹2,450.50 │
│ Type: Market     │
│ Total: ₹24,505   │
│                  │
│ [Confirm Buy]    │
└──────────────────┘
```

---

### 4. Real-Time Feedback

**Principle:** Provide near-instant feedback for all actions.

**Implementation:**

**Loading States:**
```javascript
// Order placement
<button onClick={placeOrder} disabled={loading}>
  {loading ? (
    <><Spinner /> Placing Order...</>
  ) : (
    'Buy Now'
  )}
</button>
```

**Success/Error Alerts:**
```
✅ Success Alert (Green, 3 seconds)
┌──────────────────────────────┐
│ ✓ Order Placed Successfully  │
│   Order ID: #ABC123          │
│   RELIANCE Buy 10 @ ₹2,450   │
└──────────────────────────────┘

❌ Error Alert (Red, dismissible)
┌──────────────────────────────┐
│ ✗ Order Failed               │
│   Insufficient margin        │
│   Required: ₹25,000          │
│   Available: ₹20,000         │
│   [Add Funds]  [Dismiss]     │
└──────────────────────────────┘
```

**Color Coding:**
- 🟢 Green: Profits, buy actions, success
- 🔴 Red: Losses, sell actions, errors
- 🟡 Yellow: Warnings, pending states
- 🔵 Blue: Information, neutral updates
- ⚫ Gray: Disabled, inactive states

**Live Price Updates:**
```jsx
<PriceDisplay
  symbol="RELIANCE"
  price={2450.50}
  change={+12.30}
  changePercent={+0.51}
  animate={true}  // Flash on change
  sound={true}    // Beep on significant move
/>
```

---

### 5. Smart, Customizable Dashboards

**Features:**
- Drag-and-drop panels
- Widget library (watchlist, portfolio, charts, news, orders)
- Save multiple layouts
- Dark/light modes
- Responsive grid system

**Widget Library:**
```
Available Widgets:
├── Portfolio Summary
├── Watchlist (configurable symbols)
├── Live Chart (TradingView integration)
├── Order Book
├── Trade History
├── P&L Report
├── Market News
├── Economic Calendar
├── Broker Status (multi-broker health)
└── Alerts Panel
```

**Layout Configuration:**
```javascript
// User saves custom layouts
const layouts = {
  default: { /* Grid layout config */ },
  trading: { /* Focus on charts + orders */ },
  monitoring: { /* Focus on portfolio + alerts */ },
  analysis: { /* Charts + indicators + backtest */ }
};

// Switch layouts
<LayoutSwitcher
  current="trading"
  layouts={layouts}
  onSwitch={(layout) => loadLayout(layout)}
/>
```

**Dark/Light Mode:**
```css
/* Light Mode */
:root {
  --bg-primary: #ffffff;
  --text-primary: #1a1a1a;
  --accent: #007aff;
  --profit: #34c759;
  --loss: #ff3b30;
}

/* Dark Mode */
[data-theme="dark"] {
  --bg-primary: #1c1c1e;
  --text-primary: #ffffff;
  --accent: #0a84ff;
  --profit: #30d158;
  --loss: #ff453a;
}
```

---

### 6. Transparency and Security

**Fee Disclosure:**
```
Order Preview
┌────────────────────────────────┐
│ Buy RELIANCE                   │
│ Quantity: 10 @ ₹2,450.50       │
│ ────────────────────────────   │
│ Order Value:        ₹24,505.00 │
│ Brokerage:              ₹20.00 │
│ STT:                     ₹2.45 │
│ Transaction Charges:     ₹3.00 │
│ GST:                     ₹4.55 │
│ ────────────────────────────   │
│ Total Debit:        ₹24,535.00 │
│                                │
│ [Confirm] [Cancel]             │
└────────────────────────────────┘
```

**Security Measures:**
- **2FA/Biometric Login:** Mandatory for first login, optional for subsequent
- **Session Timeout:** 15 minutes inactive, prompt before logout
- **Audit Trail:** "Last login: 2026-01-15 10:30 AM from Chrome/Mac"
- **Data Privacy:** "Your data is encrypted end-to-end. Read our Privacy Policy."

**Security UI Elements:**
```
Login Screen
┌────────────────────────────────┐
│ 🔒 Secure Login                │
│                                │
│ Username: ____________         │
│ Password: ************         │
│                                │
│ ☐ Remember me (30 days)        │
│                                │
│ [Login with Fingerprint 👆]    │
│ [Login]                        │
│                                │
│ 🔐 Your connection is encrypted│
└────────────────────────────────┘
```

---

### 7. Scalable, Modular UI

**Component Library:**
```
Design System Structure:
├── Atoms (Button, Input, Label, Icon)
├── Molecules (SearchBar, PriceCard, AlertBadge)
├── Organisms (OrderPanel, Chart, Watchlist)
├── Templates (DashboardLayout, OrderEntryFlow)
└── Pages (Home, Portfolio, Orders, Settings)
```

**Component Example:**
```jsx
// Reusable Order Button
<OrderButton
  action="buy"           // or "sell"
  symbol="RELIANCE"
  quantity={10}
  price={2450.50}
  broker="UPSTOX"       // Multi-broker support
  onSuccess={(orderId) => showAlert('success', orderId)}
  onError={(err) => showAlert('error', err.message)}
  disabled={!isBrokerHealthy('UPSTOX')}
/>
```

**Future-Proof Placeholders:**
```jsx
// Placeholder for future features
<FeatureToggle feature="options-trading">
  <OptionsPanel />
</FeatureToggle>

// Coming soon banner
{!isFeatureEnabled('algo-trading') && (
  <ComingSoonBanner feature="Algorithmic Trading" />
)}
```

---

### 8. Accessibility & Inclusivity

**WCAG 2.1 AA Compliance:**

**Color Contrast:**
- Minimum 4.5:1 for normal text
- Minimum 3:1 for large text and UI components
- Test with tools: WAVE, Axe DevTools

**Keyboard Navigation:**
```javascript
// All interactive elements accessible via keyboard
<button
  onClick={placeOrder}
  onKeyDown={(e) => e.key === 'Enter' && placeOrder()}
  tabIndex={0}
  aria-label="Place buy order for RELIANCE"
>
  Buy
</button>

// Skip navigation link
<a href="#main-content" className="sr-only sr-only-focusable">
  Skip to main content
</a>
```

**Screen Reader Support:**
```html
<!-- Semantic HTML -->
<nav aria-label="Main navigation">
  <ul>
    <li><a href="/portfolio" aria-current="page">Portfolio</a></li>
    <li><a href="/orders">Orders</a></li>
  </ul>
</nav>

<!-- ARIA labels for dynamic content -->
<div
  role="status"
  aria-live="polite"
  aria-label="Live price updates"
>
  RELIANCE: ₹2,450.50 (announced to screen reader on change)
</div>

<!-- Form accessibility -->
<label for="quantity">Quantity</label>
<input
  id="quantity"
  type="number"
  aria-required="true"
  aria-invalid={errors.quantity ? "true" : "false"}
  aria-describedby="quantity-error"
/>
{errors.quantity && (
  <span id="quantity-error" role="alert">
    {errors.quantity}
  </span>
)}
```

**High Contrast Mode:**
```css
/* Respect user preference */
@media (prefers-contrast: high) {
  :root {
    --bg-primary: #000000;
    --text-primary: #ffffff;
    --border: #ffffff;
  }
}

/* Manual high-contrast toggle */
[data-contrast="high"] {
  --profit: #00ff00;
  --loss: #ff0000;
  border-width: 2px;
}
```

---

### 9. Iterative Design and Feedback Loops

**User Feedback Mechanisms:**

1. **In-App Surveys:**
```jsx
<FeedbackPrompt
  trigger="after-first-order"
  title="How was your first order experience?"
  questions={[
    "Rate 1-5 stars",
    "What could be improved?"
  ]}
  onSubmit={(feedback) => sendToAnalytics(feedback)}
/>
```

2. **Analytics Tracking:**
```javascript
// Track user interactions
trackEvent('order_placed', {
  broker: 'UPSTOX',
  orderType: 'MARKET',
  timeToComplete: 3.2,  // seconds
  errors: 0
});

// Identify pain points
trackError('insufficient_margin', {
  page: 'order_entry',
  step: 'confirmation',
  userType: 'casual_investor'
});
```

3. **A/B Testing:**
```jsx
<ABTest
  test="order-button-color"
  variants={{
    control: <Button color="blue">Buy</Button>,
    variant: <Button color="green">Buy</Button>
  }}
  onConversion={(variant) => trackConversion(variant)}
/>
```

4. **Feature Usage Heatmaps:**
- Use Hotjar or similar to visualize clicks
- Identify unused features
- Optimize layout based on usage patterns

---

### 10. Consistent Branding & Subtlety

**Brand Guidelines:**
```css
/* Primary brand color (use sparingly) */
--brand-primary: #007aff;

/* Use for: */
- Logo
- Primary CTA buttons
- Links
- Active tab indicators

/* Avoid for: */
- Data visualization (conflicts with profit/loss colors)
- Large background areas
- Text body
```

**Tone of Voice:**
```
✅ Consistent, Professional:
- "Order placed successfully"
- "Insufficient funds. Add ₹5,000 to continue."
- "Broker connection lost. Retrying..."

❌ Inconsistent:
- "Order placed successfully"
- "Oops! You're broke! Top up now."
- "Connection dead. We're on it!"
```

**UI Copy Guidelines:**
- **Buttons:** Action-oriented (Buy, Sell, Confirm, Cancel)
- **Labels:** Descriptive, concise (Quantity, Price, Total)
- **Errors:** Specific, actionable ("Add ₹5,000" not "Insufficient funds")
- **Success:** Confirm what happened ("Order #123 placed")

---

### 11. Error Prevention & Inline Validation

**Prevent Errors Before They Happen:**

**Example 1: Insufficient Margin**
```jsx
<OrderButton
  action="buy"
  symbol="RELIANCE"
  quantity={100}
  disabled={availableMargin < requiredMargin}
  tooltip={
    availableMargin < requiredMargin
      ? `Insufficient margin. Need ₹${requiredMargin - availableMargin} more.`
      : 'Click to place order'
  }
/>
```

**Example 2: Inline Validation**
```jsx
<QuantityInput
  value={quantity}
  onChange={setQuantity}
  min={1}
  max={maxAllowed}
  validate={(val) => {
    if (val < 1) return "Minimum 1 share";
    if (val > maxAllowed) return `Maximum ${maxAllowed} shares`;
    if (val * price > availableFunds) return "Insufficient funds";
    return null;
  }}
  showValidationMessage={true}
/>

// Real-time feedback
Input: 5
✓ Valid (green checkmark)

Input: 1000
✗ Maximum 500 shares (red X + message)
```

**Example 3: Preview Before Submit**
```
Order Preview (Final Check)
┌────────────────────────────────┐
│ ⚠️  You're about to:           │
│                                │
│ Buy 100 RELIANCE               │
│ @ Market Price                 │
│ Product Type: MIS (Intraday)   │
│                                │
│ ⚠️  This order will:           │
│ - Use ₹2,45,000 margin         │
│ - Square off today by 3:20 PM  │
│ - Incur brokerage: ₹20         │
│                                │
│ [← Back] [Confirm & Place →]   │
└────────────────────────────────┘
```

---

### 12. Seamless Onboarding & Help

**First-Time User Experience:**

**Step 1: Welcome Tour**
```
┌────────────────────────────────┐
│ 👋 Welcome to AMZF Trading!    │
│                                │
│ Let's take a quick tour        │
│ (2 minutes)                    │
│                                │
│ [Start Tour] [Skip]            │
└────────────────────────────────┘

Step 1/5: This is your Portfolio
→ Shows total value and holdings

Step 2/5: This is your Watchlist
→ Add symbols to monitor prices

... (etc)
```

**Step 2: Demo Mode**
```
┌────────────────────────────────┐
│ 🎮 Try Demo Mode               │
│                                │
│ Practice trading with virtual  │
│ money before going live.       │
│                                │
│ ₹1,00,000 Virtual Cash         │
│                                │
│ [Start Demo] [Skip to Live]    │
└────────────────────────────────┘
```

**Contextual Help:**
```jsx
// Tooltip on hover/click
<InfoIcon
  tooltip="MTF (Margin Trade Funding) allows you to buy stocks with leverage. Available only on Upstox and Dhan."
/>

// Inline help text
<Label>
  Product Type
  <HelpLink href="/docs/product-types" />
</Label>

// Video tutorials
<FeatureCard title="How to Place an Order">
  <VideoTutorial src="/tutorials/place-order.mp4" />
  <TextGuide href="/docs/placing-orders" />
</FeatureCard>
```

---

## 📱 Device-Specific Considerations

### Smartphones (320px - 768px)

**Design Principles:**
- One-column layout
- Bottom navigation (thumb-friendly)
- Swipe gestures for navigation
- Minimal form fields per screen
- Large touch targets (44px minimum)

**Navigation Pattern:**
```
┌────────────────────────────────┐
│                                │
│         Content Area           │
│                                │
│                                │
└────────────────────────────────┘
  [Home] [Portfolio] [Orders] [☰]
```

**Swipe Gestures:**
- Swipe left/right: Switch between watchlist tabs
- Swipe down: Refresh prices
- Swipe up: Reveal more details
- Long press: Quick actions menu

**Quick Actions:**
```
Long press on RELIANCE
┌────────────────┐
│ Quick Actions  │
├────────────────┤
│ 📈 View Chart  │
│ 💰 Buy Now     │
│ 🔔 Set Alert   │
│ ➕ Add to List │
└────────────────┘
```

---

### Tablets (769px - 1024px)

**Design Principles:**
- Two-column layouts
- Larger panels, more content per screen
- Touch-optimized controls
- Support landscape and portrait

**Split View Example:**
```
┌──────────────┬──────────────┐
│  Watchlist   │  Chart       │
│              │              │
│ RELIANCE     │ [Candlestick │
│ SBIN         │  Chart with  │
│ INFY         │  Indicators] │
│              │              │
│ [+ Add]      │ [Timeframe]  │
└──────────────┴──────────────┘
```

**Quick Jump Navigation:**
```
[≡] Sections
├── Portfolio
├── Watchlist
├── Orders
├── Charts
└── Settings

// Tap to jump, no scrolling
```

---

### Desktop/Web (1025px+)

**Design Principles:**
- Multi-column layouts
- Dockable panels
- Keyboard shortcuts
- Multi-monitor support
- Dynamic window layouts

**Multi-Panel Layout:**
```
┌─────────┬───────────────┬─────────┐
│Watchlist│     Chart     │ Orders  │
│         │               │         │
│ RELIANCE│ [Full Chart]  │ Pending │
│ SBIN    │               │ Filled  │
│ INFY    │               │ Rejected│
│         │               │         │
├─────────┴───────────────┴─────────┤
│         Portfolio Summary         │
└───────────────────────────────────┘
```

**Workspace Presets:**
```javascript
const workspaces = {
  trading: {
    layout: 'chart-orders-watchlist',
    chartSize: 'large',
    ordersVisible: true
  },
  monitoring: {
    layout: 'portfolio-alerts-news',
    chartSize: 'small',
    ordersVisible: false
  },
  analysis: {
    layout: 'multi-chart-indicators',
    chartSize: 'extra-large',
    indicators: ['RSI', 'MACD', 'Bollinger']
  }
};
```

**Keyboard Shortcuts:**
```
Global:
  Ctrl/Cmd + B    Quick Buy
  Ctrl/Cmd + S    Quick Sell
  Ctrl/Cmd + F    Search Symbol
  Ctrl/Cmd + ,    Settings

Navigation:
  1-9             Switch workspace
  Tab             Cycle through panels
  Esc             Close modal/dismiss alert

Trading:
  B               Focus Buy panel
  S               Focus Sell panel
  Enter           Confirm order
  Esc             Cancel order
```

**Multi-Monitor Support:**
```javascript
// Detach panel to new window
<Panel
  title="Live Chart"
  detachable={true}
  onDetach={() => {
    window.open('/chart?symbol=RELIANCE', 'chart',
      'width=1200,height=800');
  }}
/>
```

---

## 🎛 Integration with Multi-Broker Architecture

### Broker Status & Health Indicators

**Status Display:**
```jsx
<BrokerStatus broker="UPSTOX">
  {({ isHealthy, status, lastUpdate }) => (
    <StatusBadge
      color={isHealthy ? 'green' : 'red'}
      icon={isHealthy ? '✓' : '✗'}
      tooltip={`Last update: ${lastUpdate}. Status: ${status}`}
    >
      {broker}
    </StatusBadge>
  )}
</BrokerStatus>

// Visual representation
🟢 UPSTOX  (Healthy, 50ms latency)
🔴 DHAN    (Degraded, connection issues)
🟡 FYERS   (Warning, 95% rate limit)
```

**Health Dashboard:**
```
Broker Health Monitor
┌────────────────────────────────────────┐
│ Broker    Status    Latency  Rate      │
├────────────────────────────────────────┤
│ UPSTOX    ✓ Healthy  50ms    40% ████  │
│ ZERODHA   ✓ Healthy  120ms   20% ██    │
│ FYERS     ⚠ Warning  250ms   95% █████ │
│ DHAN      ✗ Down     -       -         │
└────────────────────────────────────────┘
```

---

### Action Confirmation with Multi-Broker Context

**Order Preview with Broker Info:**
```
Confirm Order
┌────────────────────────────────┐
│ Broker: UPSTOX ✓ Healthy       │
│ ──────────────────────────     │
│ Buy 10 RELIANCE                │
│ @ ₹2,450.50 (Market)           │
│                                │
│ Product: MIS                   │
│ Validity: Day                  │
│                                │
│ Order Value:        ₹24,505.00 │
│ Brokerage (UPSTOX):     ₹20.00 │
│ Total:              ₹24,525.00 │
│                                │
│ ⚠️ Note: MIS orders will        │
│   square off by 3:20 PM today  │
│                                │
│ [← Back] [Confirm & Place →]   │
└────────────────────────────────┘
```

**Multi-Broker Order Routing:**
```jsx
// User can select broker if multiple configured
<BrokerSelector
  availableBrokers={['UPSTOX', 'ZERODHA']}
  defaultBroker="UPSTOX"
  showHealthStatus={true}
  onChange={(broker) => setSelectedBroker(broker)}
/>

// Display as:
Select Broker:
○ UPSTOX    (✓ 50ms, ₹20 brokerage)
○ ZERODHA   (✓ 120ms, ₹20 brokerage)
```

---

### Dynamic Forms Based on Broker Capabilities

**Broker Capability Detection:**
```jsx
import { BrokerCapabilityRegistry } from '@/broker/capability';

function OrderForm({ broker }) {
  const capability = BrokerCapabilityRegistry.getCapability(broker);

  return (
    <Form>
      {/* Always show */}
      <Input label="Quantity" />
      <Select label="Order Type" options={['Market', 'Limit']} />

      {/* Conditionally show based on broker */}
      {capability.supportsProductType('MTF') && (
        <Checkbox label="Use Margin Trade Funding (MTF)" />
      )}

      {capability.supportsGTT() && (
        <Checkbox label="Enable GTT (Good Till Triggered)" />
      )}

      {capability.supportsBracketOrders() && (
        <Fieldset label="Bracket Order">
          <Input label="Stop Loss" />
          <Input label="Target" />
        </Fieldset>
      )}
    </Form>
  );
}
```

**Capability-Based UI Messages:**
```
Upstox Order Form:
┌────────────────────────────────┐
│ Product Type:                  │
│ ○ CNC (Delivery)               │
│ ○ MIS (Intraday)               │
│ ● MTF (Margin Trade) ✨ NEW    │
└────────────────────────────────┘

Zerodha Order Form:
┌────────────────────────────────┐
│ Product Type:                  │
│ ○ CNC (Delivery)               │
│ ○ MIS (Intraday)               │
│ ○ BO (Bracket Order)           │
│ ○ CO (Cover Order)             │
│                                │
│ ℹ️ MTF not available on Zerodha│
└────────────────────────────────┘
```

---

### Real-Time Data & Alerts

**WebSocket Integration:**
```javascript
// Subscribe to price updates
const { price, change } = useLivePrice('RELIANCE', {
  broker: 'UPSTOX',
  throttle: 100  // ms between updates
});

// Display with animation
<PriceDisplay
  price={price}
  change={change}
  animate="flash"  // Flash on significant change
  sound={Math.abs(change) > 1}  // Beep if change > 1%
/>
```

**Custom Alerts:**
```
Create Price Alert
┌────────────────────────────────┐
│ Symbol: RELIANCE               │
│                                │
│ Alert me when:                 │
│ ● Price goes above ₹2,500      │
│ ○ Price goes below ₹2,400      │
│ ○ Change is greater than 2%    │
│                                │
│ Notification:                  │
│ ☑ Push notification            │
│ ☑ Email                        │
│ ☑ Sound alert                  │
│                                │
│ [Create Alert]                 │
└────────────────────────────────┘
```

**Alert Display:**
```jsx
<AlertBanner
  type="price-alert"
  symbol="RELIANCE"
  message="Price crossed ₹2,500 (Target)"
  action={
    <Button onClick={() => navigateToChart('RELIANCE')}>
      View Chart
    </Button>
  }
  sound="notification.mp3"
  dismissible={true}
/>
```

---

## 🎨 Visual Design System

### Color Palette

**Primary Colors:**
```css
:root {
  /* Brand */
  --brand-primary: #007aff;
  --brand-secondary: #5856d6;

  /* Backgrounds */
  --bg-primary: #ffffff;
  --bg-secondary: #f5f5f7;
  --bg-tertiary: #e5e5e7;

  /* Text */
  --text-primary: #1d1d1f;
  --text-secondary: #6e6e73;
  --text-tertiary: #86868b;

  /* Trading Colors */
  --profit: #34c759;
  --loss: #ff3b30;
  --neutral: #8e8e93;

  /* Status */
  --success: #34c759;
  --warning: #ff9500;
  --error: #ff3b30;
  --info: #007aff;

  /* Borders */
  --border-light: #d1d1d6;
  --border-medium: #c7c7cc;
  --border-dark: #8e8e93;
}
```

**Dark Mode:**
```css
[data-theme="dark"] {
  --bg-primary: #000000;
  --bg-secondary: #1c1c1e;
  --bg-tertiary: #2c2c2e;

  --text-primary: #ffffff;
  --text-secondary: #98989d;
  --text-tertiary: #636366;

  --profit: #30d158;
  --loss: #ff453a;

  --border-light: #38383a;
  --border-medium: #48484a;
  --border-dark: #636366;
}
```

---

### Typography

**Font Stack:**
```css
:root {
  --font-sans: -apple-system, BlinkMacSystemFont,
               'Segoe UI', 'Roboto', 'Helvetica Neue',
               sans-serif;
  --font-mono: 'SF Mono', 'Monaco', 'Courier New',
               monospace;
}

/* Sizes */
--text-xs: 0.75rem;   /* 12px - labels */
--text-sm: 0.875rem;  /* 14px - body */
--text-base: 1rem;    /* 16px - default */
--text-lg: 1.125rem;  /* 18px - headings */
--text-xl: 1.25rem;   /* 20px - prices */
--text-2xl: 1.5rem;   /* 24px - titles */

/* Weights */
--font-normal: 400;
--font-medium: 500;
--font-semibold: 600;
--font-bold: 700;
```

**Usage:**
```jsx
<Text variant="body">Portfolio Value</Text>
<Text variant="price" color="profit">₹2,45,000</Text>
<Text variant="change">+2.3%</Text>
```

---

### Spacing Scale

**Consistent Spacing:**
```css
:root {
  --space-1: 0.25rem;  /* 4px */
  --space-2: 0.5rem;   /* 8px */
  --space-3: 0.75rem;  /* 12px */
  --space-4: 1rem;     /* 16px */
  --space-5: 1.5rem;   /* 24px */
  --space-6: 2rem;     /* 32px */
  --space-8: 3rem;     /* 48px */
  --space-10: 4rem;    /* 64px */
}
```

---

### Component Library

**Button Variants:**
```jsx
// Primary (brand color)
<Button variant="primary">Place Order</Button>

// Buy (green)
<Button variant="buy">Buy</Button>

// Sell (red)
<Button variant="sell">Sell</Button>

// Secondary (outline)
<Button variant="secondary">Cancel</Button>

// Ghost (text only)
<Button variant="ghost">Learn More</Button>

// Sizes
<Button size="sm">Small</Button>
<Button size="md">Medium</Button>  // default
<Button size="lg">Large</Button>
```

**Card Component:**
```jsx
<Card>
  <Card.Header>
    <Card.Title>Portfolio</Card.Title>
    <Card.Action>
      <IconButton icon="refresh" onClick={refresh} />
    </Card.Action>
  </Card.Header>
  <Card.Body>
    <PortfolioSummary />
  </Card.Body>
  <Card.Footer>
    <Link href="/portfolio/details">View Details →</Link>
  </Card.Footer>
</Card>
```

---

## 📊 Data Visualization Guidelines

### Charts

**Chart Types:**
- **Candlestick:** Price action (default)
- **Line:** Simple trend view
- **Area:** Filled line chart
- **Bar:** Volume display

**Integration:**
```jsx
import { TradingViewWidget } from '@/components/charts';

<TradingViewWidget
  symbol="NSE:RELIANCE"
  interval="5"
  theme={theme}  // auto-sync with app theme
  studies={['RSI', 'MACD']}
  height={400}
/>
```

---

### Tables

**Order Table:**
```jsx
<Table>
  <Table.Head>
    <Table.Row>
      <Table.Cell>Time</Table.Cell>
      <Table.Cell>Symbol</Table.Cell>
      <Table.Cell>Type</Table.Cell>
      <Table.Cell align="right">Qty</Table.Cell>
      <Table.Cell align="right">Price</Table.Cell>
      <Table.Cell>Status</Table.Cell>
    </Table.Row>
  </Table.Head>
  <Table.Body>
    {orders.map(order => (
      <Table.Row key={order.id}>
        <Table.Cell>{formatTime(order.time)}</Table.Cell>
        <Table.Cell>
          <SymbolLink symbol={order.symbol} />
        </Table.Cell>
        <Table.Cell>
          <Badge color={order.type === 'BUY' ? 'green' : 'red'}>
            {order.type}
          </Badge>
        </Table.Cell>
        <Table.Cell align="right">{order.quantity}</Table.Cell>
        <Table.Cell align="right">
          <MoneyDisplay value={order.price} />
        </Table.Cell>
        <Table.Cell>
          <StatusBadge status={order.status} />
        </Table.Cell>
      </Table.Row>
    ))}
  </Table.Body>
</Table>
```

---

## ✅ Implementation Checklist

### Phase 1: Foundation
- [ ] Set up design system (colors, typography, spacing)
- [ ] Create component library (buttons, inputs, cards)
- [ ] Implement responsive grid system
- [ ] Set up dark/light theme switching
- [ ] Configure accessibility tools (linters, tests)

### Phase 2: Core Features
- [ ] Build dashboard with customizable panels
- [ ] Implement order entry flow (mobile-first)
- [ ] Create portfolio summary view
- [ ] Build watchlist with live prices
- [ ] Integrate charts (TradingView)

### Phase 3: Multi-Broker Integration
- [ ] Display broker health indicators
- [ ] Dynamic forms based on broker capabilities
- [ ] Order preview with broker-specific fees
- [ ] Multi-broker order routing UI
- [ ] Broker switching functionality

### Phase 4: Real-Time & Alerts
- [ ] WebSocket integration for live prices
- [ ] Custom price alerts
- [ ] Push notifications
- [ ] Sound alerts
- [ ] Market news feed

### Phase 5: Polish & Optimization
- [ ] Accessibility audit (WCAG 2.1 AA)
- [ ] Performance optimization (code splitting)
- [ ] Error boundaries and fallbacks
- [ ] Loading states and skeletons
- [ ] Empty states and onboarding

### Phase 6: Testing & Launch
- [ ] Usability testing with real users
- [ ] A/B testing for critical flows
- [ ] Analytics implementation
- [ ] Feedback mechanisms
- [ ] Production monitoring

---

## 📚 Resources

### Design Tools
- **Figma:** UI design and prototyping
- **Storybook:** Component documentation
- **Chromatic:** Visual regression testing

### Testing Tools
- **Axe DevTools:** Accessibility testing
- **Lighthouse:** Performance auditing
- **WAVE:** Web accessibility evaluation

### Inspiration
- **Zerodha Kite:** Clean Indian trading UI
- **Robinhood:** Simple mobile-first design
- **TradingView:** Advanced charting
- **Interactive Brokers:** Professional desktop layout

---

## 🔄 Continuous Improvement

### User Feedback Loop
1. **Release** feature to production
2. **Monitor** analytics and error rates
3. **Gather** feedback via surveys
4. **Analyze** usage patterns
5. **Iterate** on design improvements
6. **Test** A/B variants
7. **Repeat** cycle

### Success Metrics
- **Task Completion Rate:** > 95%
- **Time to First Order:** < 2 minutes
- **Error Rate:** < 1%
- **User Satisfaction:** > 4.5/5 stars
- **Daily Active Users:** Growth trend
- **Session Duration:** Optimal engagement

---

## 📞 Contact & Support

For questions about these UI guidelines:
- **Design Team:** design@amzf.in
- **Frontend Team:** frontend@amzf.in
- **Documentation:** https://docs.amzf.in/ui-guidelines

**Last Updated:** 2026-01-15
**Version:** 1.0
**Maintained by:** AMZF Product Team
