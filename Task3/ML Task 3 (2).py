import random
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, confusion_matrix, ConfusionMatrixDisplay, classification_report

print("Please note: Output may differ depending on model results.")
print("################################################################################################")

#--------------------------------------------
# !Preprocessing w/ panda!
loadedFile = pd.read_csv("Sample.csv") # Load dataset with panda

rowCount = loadedFile.shape[0] # shape[0] = rows
print("Rows: " + str(rowCount)) # Print NumOfRows
print()

# Dropped unwanted columns before training
file = loadedFile.drop(columns=["SchedType", "CoreCount", "CoreId", "IsCoreIdle"])

# Replace invalid numeric values
file = file.replace([np.inf, -np.inf], np.nan) # Convert inf and -inf to NaN
file = file.fillna(0) # Fill NaNs with 0

# Action value counts, #ofFeatures, and Feature names
print("Action counts overall: ")
print(file["Action"].value_counts()) # Print action value counts
print()

removedAction = file.drop(columns=["Action"])
featCount = removedAction.shape[1] # shape[1] = columns
print("Number of features: " + str(featCount)) # Print #ofFeats

print("Feature names: " + str(removedAction.columns.tolist())) # Print Feats
print()
#--------------------------------------------



#--------------------------------------------
# !Model Training w/ scikit!

# Split dataset into train/test sets using 80/20 split.
actionMap = {'NEXT_FCFS':0,'NEXT_OTHER':1,'NEXT_RR':2, 'NEXT_SJF':3, 'NONE':4}
y = file["Action"].map(actionMap) # I think I do this, Max (you were close buddy you had to seperate them though)
x = removedAction


x_train, x_test, y_train, y_test = train_test_split(x, y, test_size=0.2)

# Train a RandomForestClassifier
mySon = RandomForestClassifier(n_estimators=random.randint(500, 2000), random_state=42, n_jobs=-1)
mySon.fit(x_train, y_train)

pred = mySon.predict(x_test)

# Print Accuracy, Classification report, and Confusion Matrix

acc = accuracy_score(y_test, pred) # Accuracy
print("Accuracy:" + str(acc))

print(classification_report(y_test, pred)) # Classification Report

print(confusion_matrix(y_test, pred)) # Confusion Matrix
#--------------------------------------------



#--------------------------------------------
# !User Input Required!

print("======================================================================")
print("SIMPLE SIMULATION (ML predicts next queue-level action)") # Primt
print("======================================================================")

# Num of Processes n (min 2)
NumOfProc = int(input("How many processes? (min 2): "))

while NumOfProc < 2:
    print("Minimum is 2. Try again.")
    NumOfProc = int(input("How many processes? (min 2): "))

# RR Time Quantum
RRQuantum = int(input("RR quantum (e.g., 2): "))

# For each process ask for Arrival and Burst Time
ArrivalArray = [] # Storage arrays
BurstArray = []
for x in range(NumOfProc): # Fill arrays with user input
    checker = False
    while checker is False:
        try:
            arrivalvalue = int(input("P" + str(x) + " arrival time: "))
            burstvalue = int(input("P" + str(x) + " burst time: "))
            if arrivalvalue < 0 or burstvalue <= 0:
                print("No")
            else:
                ArrivalArray.append(arrivalvalue)
                BurstArray.append(burstvalue)
                checker = True
        except ValueError:
            print("Failed.")
#--------------------------------------------



#--------------------------------------------
# !Simulator Logic!

print("======================================================================")
print("SCHEDULER DECISION LOG")
print("======================================================================")

#--------------------------------------------

#Empty action list to store the action types from the action map
actions = {}
for x, i in y.items():
    actions[i] = x
    
# The amount of columns and their names that the model was trained on
featuressize = removedAction.columns

# The ticks for RR quantum until it reaches the specified quantum above
quantums = 0

# Total burst time total
burststhatran = 0.0

# The current tick time
time = 0

# the number of processes done
done = 0

#A deepcopy of burstarray for the bursttimes remaining (and so we dont impact burstarray itself)
burstremaining = BurstArray[:]

# waiting to enter the queue
current = []

# PID currently on the CPU
running = None

# Makes a boolean array for every process individually
completed = [False]*NumOfProc

# this stores the scheduler decision log classification_report
frame = []

#while done processes is less than the number of processes specified
while done < NumOfProc:
    
    #checks every process and adds it to the waiting to enter the queue
    for x in range(NumOfProc):
        if ArrivalArray[x] <= time and burstremaining[x] > 0 and x != running and x not in current:
            current.append(x)
            
    # Add processes that arrive at the current time to the ready queue
    if running is None and not current:
        frame.append({"Time": time, "ReadyQueue":"[]", "Action": None, "Chosen":"IDLE","RemainingAfter": None})
        time = time + 1
        continue
    
    #remaining action burst times for the whole readyqueue
    remainingactionRQ = []
    
    #for all the actions left still with burst time remaining, add their times to the remainingaction list
    for x in current:
        remainingactionRQ.append(burstremaining[x])
    
    #set the remaining_time = [0] even the ones that arent in the current runs
    if not remainingactionRQ:
        remainingactionRQ = [0]
    
    #features are all set to 0.0
    features = dict.fromkeys(featuressize, 0.0)
    
    #QueueThreadCount: Length of the ready queue (number of processes currently ready to run).
    #QueueMinRemainingBursts: Smallest remaining burst time among all processes in the ready queue.
    #QueueMaxRemainingBursts: Largest remaining burst time among all processes in the ready queue.
    #QueueMeanRemainingBursts: Average remaining burst time of all processes in the ready queue
    #QueueTotalRemainingBursts: Sum of the remaining burst times of all processes in the ready queue
    #ThreadBurstRan: Total number of CPU bursts executed so far across all processes
    #ThreadBurstsRemaining: – Total remaining burst time across all processes (initial total bursts − 
    #bursts already executed).

    features.update({
    "QueueThreadCount": len(current),
    "QueueMinRemainingBursts": min(remainingactionRQ),
    "QueueMaxRemainingBursts": max(remainingactionRQ),
    "QueueMeanRemainingBursts": sum(remainingactionRQ) / len(remainingactionRQ),
    "QueueTotalRemainingBursts": sum(remainingactionRQ),
    "ThreadBurstsRan": burststhatran,
    "ThreadBurstsRemaining": sum(BurstArray) - burststhatran,
    })
    
    #new frame of all the features based on the model
    new = pd.DataFrame([features], columns=features)
    
    #prediction index based on the model
    meow = int(mySon.predict(new)[0])
    
    #the chosen action for the process (does a cool flip. it could be a backflip or frontflipo. we will see)
    sonPerformance = actions[meow]
    
    #If model predicts NEXT_OTHER → set action to NEXT_FCFS
    #If model predicts NONE and process is not running → set action to NEXT_FCFS
    if sonPerformance == "NEXT_OTHER" or (sonPerformance == "NONE" and running is None):
        sonPerformance = "NEXT_FCFS"
    
    # do not switch processes; keep running current process
    if sonPerformance == "NONE":
        chosen = running
        
    # choose the ready process with earliest arrival
    elif sonPerformance == "NEXT_FCFS":
        #copy of the processes, specifically only the ones running currently
        processtochoosefrom = current[:]
        if running is not None:
            processtochoosefrom.append(running)
        #choses closes action in ready queue
        chosen = processtochoosefrom[0]
        
        #among the processes, choose the one with the earliest arrival time from the arrival array
        for x in processtochoosefrom:
            if (ArrivalArray[x], x) < (ArrivalArray[chosen], chosen):
                chosen = x
    # choose the ready process with the smallest remaining burst
    elif sonPerformance == "NEXT_SJF":
        
        #copy of the processes, specifically only the ones running currently
        processtochoosefrom = current[:]
        if running is not None:
            processtochoosefrom.append(running)
        #choses closes action in ready queue
        chosen = processtochoosefrom[0]
        
        #among the processes, choose the one with the smallest remaining burst time
        for x in processtochoosefrom:
            if (burstremaining[x], x) < (burstremaining[chosen], chosen):
                chosen = x
    
    #choose the next precess in the ready queue and run using RR quantum
    else:
        if running is not None and quantums < RRQuantum:
            chosen = running
        elif current:
            chosen = current[0]
        else:
            quantums = 0

    # if the chosen action is not the one from before
    if chosen != running:
        if running is not None:
            current.append(running)
        #remove the chosen action from the current waiting list
        current.remove(chosen)
        
        #reset quantum for the new currently running process
        quantums = 0
        
    #the new currently runnning process is chosen
    running = chosen
    
    #run one tick
    ran = min(1, burstremaining[running])
    
    #updating all of the values for this one tick
    burststhatran = burststhatran + ran
    quantums = quantums + 1
    burstremaining[running] = burstremaining[running] - ran
    
    #the process for this current action
    theprocess = ["P" + str(x) for x in current]
    
    #add the frame to the list of all frames
    frame.append({"Time": time, "ReadyQueue": "[" + ", ".join(theprocess) + "]", "Action": sonPerformance, 
                    "Chosen": "P" + str(running), "RemainingAfter": burstremaining[running]})
                    
    #Remove completed processes from the ready queue and clear the CPU/quantums
    if burstremaining[running] <= 0:
        completed[running] = True
        done = done + 1
        running = None
        quantums = 0
        
    #update time by 1
    time = time + 1
#printing the dataframe to a string table
frame = pd.DataFrame(frame)
print(frame.to_string())