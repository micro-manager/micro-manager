(ns optimization
  (:import [org.apache.commons.math.optimization GoalType]
           [org.apache.commons.math.optimization.univariate BrentOptimizer]
           [org.apache.commons.math.optimization.direct NelderMead]
           [org.apache.commons.math.analysis UnivariateRealFunction MultivariateRealFunction]))

(defn brent-min
  "Find the minimum of univariate function f
  using Brent's method."
  [f xmin xmax]
  (.optimize
    (BrentOptimizer.)
    (reify UnivariateRealFunction
      (value [_ x] (f x)))
    GoalType/MINIMIZE xmin xmax))


(defn nelder-mead-min
  "Find the minimum of multivariate function f
  using the Nelder-Mead method. f should be a
  function of a vector of values with the same
  length as stepsizes."
  [f stepsizes]
  (->
    (.optimize
      (NelderMead.)
      (reify MultivariateRealFunction
        (value [_ x] (apply f x)))
      GoalType/MINIMIZE (double-array stepsizes))
    .getPoint vec))