package cn.blockforge.generated.thundercloud.animation;

/** 服务端时间轴；只在生成和开始驱散时同步，客户端按世界时间平滑取样。 */
public record CloudLifecycle(long createdAt, int formationTicks, long dismissedAt, int dissipationTicks) {
    public static final int MAX_ANIMATION_TICKS = 1200;

    public CloudLifecycle {
        formationTicks = Math.max(0, Math.min(MAX_ANIMATION_TICKS, formationTicks));
        dissipationTicks = Math.max(0, Math.min(MAX_ANIMATION_TICKS, dissipationTicks));
    }

    /** 0 关闭对应动画；负数和非有限数回退到3秒；最多60秒。 */
    public static int secondsToTicks(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0.0) seconds = 3.0;
        return (int) Math.ceil(Math.min(60.0, seconds) * 20.0);
    }

    public static CloudLifecycle summon(long now, int formationTicks) {
        return new CloudLifecycle(now, formationTicks, -1L, 0);
    }

    public boolean isDissipating() { return dismissedAt >= 0L; }

    /** 连续驱散不会重新计时；形成途中驱散时冻结当前形成进度。 */
    public CloudLifecycle dismiss(long now, int ticks) {
        return isDissipating() ? this : new CloudLifecycle(createdAt, formationTicks, now, ticks);
    }

    public boolean finished(long now) {
        return isDissipating() && now - dismissedAt >= dissipationTicks;
    }

    public Frame sample(double now) {
        double formationTime = isDissipating() ? Math.min(now, dismissedAt) : now;
        return new Frame(progress(formationTime - createdAt, formationTicks),
                isDissipating() ? progress(now - dismissedAt, dissipationTicks) : 0.0);
    }

    private static double progress(double elapsed, int duration) {
        return duration == 0 ? 1.0 : clamp(elapsed / duration);
    }

    public static double clamp(double value) { return Math.max(0.0, Math.min(1.0, value)); }
    public static double smooth(double value) {
        double t = clamp(value);
        return t * t * (3.0 - 2.0 * t);
    }

    public record Frame(double formation, double dissipation) {
        public static final Frame STEADY = new Frame(1.0, 0.0);
        public boolean visible() { return formation > 0.0 && dissipation < 1.0; }
        public boolean steady() { return formation >= 1.0 && dissipation <= 0.0; }
        // 仅最初少量薄雾和驱散期间使用透明层；形成后的主体始终写入不透明深度。
        public boolean solid() { return formation >= 0.18 && dissipation <= 0.0; }
    }
}
